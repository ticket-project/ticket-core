package com.ticket.booking.internal.application.order.command;

import com.ticket.booking.OrderTerminated;
import com.ticket.core.support.exception.CoreException;
import com.ticket.core.support.exception.ErrorType;
import com.ticket.booking.internal.domain.hold.command.HoldHistoryRecorder;
import com.ticket.booking.internal.domain.order.model.Order;
import com.ticket.booking.internal.domain.order.model.OrderSeat;
import com.ticket.booking.internal.domain.order.model.OrderState;
import com.ticket.booking.internal.domain.order.repository.OrderSeatRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderTerminationServiceTest {

    private static final LocalDateTime FIXED_NOW = LocalDateTime.of(2026, 3, 15, 10, 0);
    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-03-15T01:00:00Z"), ZoneId.of("Asia/Seoul"));

    @Mock
    private OrderSeatRepository orderSeatRepository;

    @Mock
    private HoldHistoryRecorder holdHistoryRecorder;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Test
    void cancel_changes_state_records_history_and_publishes_order_terminated() {
        final Order order = order(10L, "hold-key");
        final OrderSeat orderSeat = new OrderSeat(order, 501L, 42L, BigDecimal.TEN);
        when(orderSeatRepository.findAllByOrderIdOrderByIdAsc(10L)).thenReturn(List.of(orderSeat));

        service().cancel(order, FIXED_NOW);

        assertThat(order.getStatus()).isEqualTo(OrderState.CANCELED);
        verify(holdHistoryRecorder).recordCanceled(1L, 100L, "hold-key", FIXED_NOW, List.of(orderSeat));
        final OrderTerminated event = capturedEvent();
        assertThat(event.orderId()).isEqualTo(10L);
        assertThat(event.memberId()).isEqualTo(1L);
        assertThat(event.holdKey()).isEqualTo("hold-key");
        assertThat(event.performanceSeatIds()).isEqualTo(Set.of(501L));
        assertThat(event.reason()).isEqualTo("CANCELED");
        assertThat(event.occurredAt()).isEqualTo(FIXED_NOW.atZone(ZoneId.of("Asia/Seoul")).toInstant());
    }

    @Test
    void expire_changes_state_records_history_and_publishes_order_terminated() {
        final Order order = order(10L, "hold-key");
        final OrderSeat orderSeat = new OrderSeat(order, 501L, 42L, BigDecimal.TEN);
        when(orderSeatRepository.findAllByOrderIdOrderByIdAsc(10L)).thenReturn(List.of(orderSeat));

        service().expire(order, FIXED_NOW);

        assertThat(order.getStatus()).isEqualTo(OrderState.EXPIRED);
        verify(holdHistoryRecorder).recordExpired(1L, 100L, "hold-key", FIXED_NOW, List.of(orderSeat));
        final OrderTerminated event = capturedEvent();
        assertThat(event.reason()).isEqualTo("EXPIRED");
    }

    @Test
    void foreign_order_seat_stops_the_entire_termination_flow() {
        final Order order = order(10L, "hold-key");
        final Order otherOrder = order(11L, "other-hold-key");
        final OrderSeat foreignOrderSeat = new OrderSeat(otherOrder, 501L, 42L, BigDecimal.TEN);
        when(orderSeatRepository.findAllByOrderIdOrderByIdAsc(10L)).thenReturn(List.of(foreignOrderSeat));

        assertThatThrownBy(() -> service().expire(order, FIXED_NOW))
                .isInstanceOf(CoreException.class)
                .satisfies(error -> assertThat(((CoreException) error).getErrorType())
                        .isEqualTo(ErrorType.INVALID_REQUEST));

        assertThat(order.getStatus()).isEqualTo(OrderState.PENDING);
        verifyNoInteractions(holdHistoryRecorder, eventPublisher);
    }

    private OrderTerminated capturedEvent() {
        final ArgumentCaptor<OrderTerminated> captor = ArgumentCaptor.forClass(OrderTerminated.class);
        verify(eventPublisher).publishEvent(captor.capture());
        return captor.getValue();
    }

    private OrderTerminationService service() {
        return new OrderTerminationService(
                orderSeatRepository,
                holdHistoryRecorder,
                eventPublisher,
                FIXED_CLOCK
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
