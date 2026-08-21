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
class OrderTerminationServiceTest {

    private static final LocalDateTime FIXED_NOW = LocalDateTime.of(2026, 3, 15, 10, 0);

    @Mock
    private OrderSeatRepository orderSeatRepository;

    @Mock
    private HoldHistoryRecorder holdHistoryRecorder;

    @Mock
    private HoldReleaseOutboxWriter holdReleaseOutboxWriter;

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @Test
    void cancel_changes_state_records_history_and_requests_hold_release() {
        final Order order = order(10L, "hold-key");
        final OrderSeat orderSeat = new OrderSeat(order, 501L, 42L, BigDecimal.TEN);
        when(orderSeatRepository.findAllByOrder_IdOrderByIdAsc(10L)).thenReturn(List.of(orderSeat));
        when(holdReleaseOutboxWriter.append(
                new OrderTerminationResult(100L, "hold-key", List.of(42L)),
                HoldReleaseReason.USER_CANCELED
        )).thenReturn(99L);

        service().cancel(order, FIXED_NOW);

        assertThat(order.getStatus()).isEqualTo(OrderState.CANCELED);
        verify(holdHistoryRecorder).recordCanceled(1L, 100L, "hold-key", FIXED_NOW, List.of(orderSeat));
        verify(applicationEventPublisher).publishEvent(new HoldReleaseRequestedEvent(99L));
    }

    @Test
    void expire_changes_state_records_history_and_requests_hold_release() {
        final Order order = order(10L, "hold-key");
        final OrderSeat orderSeat = new OrderSeat(order, 501L, 42L, BigDecimal.TEN);
        when(orderSeatRepository.findAllByOrder_IdOrderByIdAsc(10L)).thenReturn(List.of(orderSeat));
        when(holdReleaseOutboxWriter.append(
                new OrderTerminationResult(100L, "hold-key", List.of(42L)),
                HoldReleaseReason.ORDER_EXPIRED
        )).thenReturn(99L);

        service().expire(order, FIXED_NOW);

        assertThat(order.getStatus()).isEqualTo(OrderState.EXPIRED);
        verify(holdHistoryRecorder).recordExpired(1L, 100L, "hold-key", FIXED_NOW, List.of(orderSeat));
        verify(applicationEventPublisher).publishEvent(new HoldReleaseRequestedEvent(99L));
    }

    @Test
    void foreign_order_seat_stops_the_entire_termination_flow() {
        final Order order = order(10L, "hold-key");
        final Order otherOrder = order(11L, "other-hold-key");
        final OrderSeat foreignOrderSeat = new OrderSeat(otherOrder, 501L, 42L, BigDecimal.TEN);
        when(orderSeatRepository.findAllByOrder_IdOrderByIdAsc(10L)).thenReturn(List.of(foreignOrderSeat));

        assertThatThrownBy(() -> service().expire(order, FIXED_NOW))
                .isInstanceOf(CoreException.class)
                .satisfies(error -> assertThat(((CoreException) error).getErrorType())
                        .isEqualTo(ErrorType.INVALID_REQUEST));

        assertThat(order.getStatus()).isEqualTo(OrderState.PENDING);
        verifyNoInteractions(holdHistoryRecorder, holdReleaseOutboxWriter, applicationEventPublisher);
    }

    private OrderTerminationService service() {
        return new OrderTerminationService(
                orderSeatRepository,
                holdHistoryRecorder,
                holdReleaseOutboxWriter,
                applicationEventPublisher
        );
    }

    private Order order(final Long id, final String holdKey) {
        final Order order = new Order(
                1L,
                100L,
                "order-" + id,
                holdKey,
                BigDecimal.TEN,
                FIXED_NOW.plusMinutes(5)
        );
        ReflectionTestUtils.setField(order, "id", id);
        return order;
    }
}
