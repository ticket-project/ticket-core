package com.ticket.core.domain.order.command;

import com.ticket.core.domain.hold.command.HoldHistoryRecorder;
import com.ticket.core.domain.hold.model.HoldReleaseReason;
import com.ticket.core.domain.order.OrderTerminationResult;
import com.ticket.core.domain.order.command.release.HoldReleaseOutboxWriter;
import com.ticket.core.domain.order.command.release.HoldReleaseRequestedEvent;
import com.ticket.core.domain.order.model.Order;
import com.ticket.core.domain.order.model.OrderSeat;
import com.ticket.core.domain.order.model.OrderState;
import com.ticket.core.domain.order.repository.OrderSeatRepository;
import com.ticket.core.domain.performanceseat.model.PerformanceSeat;
import com.ticket.core.domain.performanceseat.model.PerformanceSeatState;
import com.ticket.core.domain.performanceseat.repository.PerformanceSeatRepository;
import com.ticket.core.support.exception.CoreException;
import com.ticket.core.support.exception.ErrorType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
class OrderConfirmationServiceTest {

    private static final LocalDateTime FIXED_NOW = LocalDateTime.of(2026, 8, 21, 10, 0);

    @Mock
    private OrderSeatRepository orderSeatRepository;

    @Mock
    private PerformanceSeatRepository performanceSeatRepository;

    @Mock
    private HoldHistoryRecorder holdHistoryRecorder;

    @Mock
    private HoldReleaseOutboxWriter holdReleaseOutboxWriter;

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @Test
    void 확정하면_주문과_좌석을_전이하고_결제확정_이유로_해제를_요청한다() {
        final Order order = order();
        final OrderSeat orderSeat = new OrderSeat(order, 501L, 42L, BigDecimal.TEN);
        final PerformanceSeat performanceSeat = performanceSeat(501L, PerformanceSeatState.AVAILABLE);
        when(orderSeatRepository.findAllByOrder_IdOrderByIdAsc(10L)).thenReturn(List.of(orderSeat));
        when(performanceSeatRepository.findAllById(List.of(501L))).thenReturn(List.of(performanceSeat));
        when(holdReleaseOutboxWriter.append(
                new OrderTerminationResult(100L, "hold-key", List.of(42L)),
                HoldReleaseReason.PAYMENT_CONFIRMED
        )).thenReturn(99L);

        service().confirm(order, FIXED_NOW);

        assertThat(order.getStatus()).isEqualTo(OrderState.CONFIRMED);
        assertThat(order.getConfirmedAt()).isEqualTo(FIXED_NOW);
        assertThat(performanceSeat.getState()).isEqualTo(PerformanceSeatState.RESERVED);
        verify(holdHistoryRecorder).recordConfirmed(1L, 100L, "hold-key", FIXED_NOW, List.of(orderSeat));
        verify(applicationEventPublisher).publishEvent(new HoldReleaseRequestedEvent(99L));
    }

    @Test
    void 좌석이_이미_예매완료면_아무것도_바꾸지_않고_예외를_던진다() {
        final Order order = order();
        final OrderSeat orderSeat = new OrderSeat(order, 501L, 42L, BigDecimal.TEN);
        final PerformanceSeat performanceSeat = performanceSeat(501L, PerformanceSeatState.RESERVED);
        when(orderSeatRepository.findAllByOrder_IdOrderByIdAsc(10L)).thenReturn(List.of(orderSeat));
        when(performanceSeatRepository.findAllById(List.of(501L))).thenReturn(List.of(performanceSeat));

        assertThatThrownBy(() -> service().confirm(order, FIXED_NOW))
                .isInstanceOf(CoreException.class)
                .satisfies(error -> assertThat(((CoreException) error).getErrorType())
                        .isEqualTo(ErrorType.SEAT_ALREADY_RESERVED));

        assertThat(order.getStatus()).isEqualTo(OrderState.PENDING);
        verifyNoInteractions(holdHistoryRecorder, holdReleaseOutboxWriter, applicationEventPublisher);
    }

    @Test
    void 회차좌석을_일부만_찾으면_예외를_던진다() {
        final Order order = order();
        final OrderSeat first = new OrderSeat(order, 501L, 42L, BigDecimal.TEN);
        final OrderSeat second = new OrderSeat(order, 502L, 43L, BigDecimal.TEN);
        when(orderSeatRepository.findAllByOrder_IdOrderByIdAsc(10L)).thenReturn(List.of(first, second));
        when(performanceSeatRepository.findAllById(List.of(501L, 502L)))
                .thenReturn(List.of(performanceSeat(501L, PerformanceSeatState.AVAILABLE)));

        assertThatThrownBy(() -> service().confirm(order, FIXED_NOW))
                .isInstanceOf(CoreException.class)
                .satisfies(error -> assertThat(((CoreException) error).getErrorType())
                        .isEqualTo(ErrorType.SEAT_MISMATCH_IN_PERFORMANCE));

        assertThat(order.getStatus()).isEqualTo(OrderState.PENDING);
    }

    @Test
    void 다른_주문의_좌석이_섞여_있으면_예외를_던진다() {
        final Order order = order();
        final Order otherOrder = new Order(2L, 100L, "other-key", "other-hold", BigDecimal.TEN, FIXED_NOW.plusMinutes(5));
        ReflectionTestUtils.setField(otherOrder, "id", 11L);
        final OrderSeat foreign = new OrderSeat(otherOrder, 501L, 42L, BigDecimal.TEN);
        when(orderSeatRepository.findAllByOrder_IdOrderByIdAsc(10L)).thenReturn(List.of(foreign));

        assertThatThrownBy(() -> service().confirm(order, FIXED_NOW))
                .isInstanceOf(CoreException.class)
                .satisfies(error -> assertThat(((CoreException) error).getErrorType())
                        .isEqualTo(ErrorType.INVALID_REQUEST));

        assertThat(order.getStatus()).isEqualTo(OrderState.PENDING);
        verifyNoInteractions(performanceSeatRepository, holdHistoryRecorder, holdReleaseOutboxWriter, applicationEventPublisher);
    }

    private OrderConfirmationService service() {
        return new OrderConfirmationService(
                orderSeatRepository,
                performanceSeatRepository,
                holdHistoryRecorder,
                holdReleaseOutboxWriter,
                applicationEventPublisher
        );
    }

    private Order order() {
        final Order order = new Order(1L, 100L, "order-key", "hold-key", BigDecimal.TEN, FIXED_NOW.plusMinutes(5));
        ReflectionTestUtils.setField(order, "id", 10L);
        return order;
    }

    private PerformanceSeat performanceSeat(final Long id, final PerformanceSeatState state) {
        final PerformanceSeat performanceSeat = new PerformanceSeat(null, null, state, BigDecimal.TEN);
        ReflectionTestUtils.setField(performanceSeat, "id", id);
        return performanceSeat;
    }
}
