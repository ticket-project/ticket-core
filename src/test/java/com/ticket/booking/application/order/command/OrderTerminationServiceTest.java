package com.ticket.booking.application.order.command;

import com.ticket.booking.OrderTerminated;
import com.ticket.booking.domain.hold.command.HoldHistoryRecorder;
import com.ticket.booking.domain.order.model.Order;
import com.ticket.booking.domain.order.model.OrderSeat;
import com.ticket.booking.domain.order.model.OrderState;
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
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OrderTerminationServiceTest {

    private static final LocalDateTime FIXED_NOW = LocalDateTime.of(2026, 3, 15, 10, 0);
    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-03-15T01:00:00Z"), ZoneId.of("Asia/Seoul"));

    @Mock
    private HoldHistoryRecorder holdHistoryRecorder;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Test
    void cancel_changes_state_records_history_and_publishes_order_terminated() {
        final Order order = order(10L, "hold-key");
        final OrderSeat orderSeat = order.addOrderSeat(501L, 42L, BigDecimal.TEN, "R", "R석", "1F 가구역 A열 1번");

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
        final OrderSeat orderSeat = order.addOrderSeat(501L, 42L, BigDecimal.TEN, "R", "R석", "1F 가구역 A열 1번");

        service().expire(order, FIXED_NOW);

        assertThat(order.getStatus()).isEqualTo(OrderState.EXPIRED);
        verify(holdHistoryRecorder).recordExpired(1L, 100L, "hold-key", FIXED_NOW, List.of(orderSeat));
        final OrderTerminated event = capturedEvent();
        assertThat(event.reason()).isEqualTo("EXPIRED");
    }

    @Test
    void 여러_좌석은_id_오름차순_그대로_종료_처리에_넘어간다() {
        final Order order = order(10L, "hold-key");
        final OrderSeat first = order.addOrderSeat(501L, 42L, BigDecimal.TEN, "R", "R석", "1F 가구역 A열 1번");
        final OrderSeat second = order.addOrderSeat(502L, 43L, BigDecimal.TEN, "R", "R석", "1F 가구역 A열 2번");

        service().cancel(order, FIXED_NOW);

        verify(holdHistoryRecorder).recordCanceled(1L, 100L, "hold-key", FIXED_NOW, List.of(first, second));
        assertThat(capturedEvent().performanceSeatIds()).isEqualTo(Set.of(501L, 502L));
    }

    private OrderTerminated capturedEvent() {
        final ArgumentCaptor<OrderTerminated> captor = ArgumentCaptor.forClass(OrderTerminated.class);
        verify(eventPublisher).publishEvent(captor.capture());
        return captor.getValue();
    }

    private OrderTerminationService service() {
        return new OrderTerminationService(
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
                FIXED_NOW.plusMinutes(5),
                "show-title",
                FIXED_NOW.plusDays(1),
                "venue-name"
        );
        ReflectionTestUtils.setField(order, "id", id);
        return order;
    }
}
