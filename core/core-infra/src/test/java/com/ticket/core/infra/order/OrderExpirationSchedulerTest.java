package com.ticket.core.infra.order;

import com.ticket.core.domain.order.command.expire.ExpireOrderUseCase;
import com.ticket.core.domain.order.model.Order;
import com.ticket.core.domain.order.model.OrderState;
import com.ticket.core.domain.order.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("NonAsciiCharacters")
@ExtendWith(MockitoExtension.class)
class OrderExpirationSchedulerTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ExpireOrderUseCase expireOrderUseCase;

    private final Clock fixedClock = Clock.fixed(Instant.parse("2026-03-15T01:00:00Z"), ZoneId.of("Asia/Seoul"));

    @Test
    void no_expired_orders_returns_immediately() {
        OrderExpirationScheduler scheduler = new OrderExpirationScheduler(expireOrderUseCase, orderRepository, fixedClock);
        LocalDateTime expectedNow = LocalDateTime.of(2026, 3, 15, 10, 0);
        when(orderRepository.findAllByStatusAndExpiresAtLessThanEqual(eq(OrderState.PENDING), eq(expectedNow), any()))
                .thenReturn(new SliceImpl<>(List.of()));

        scheduler.expirePendingOrders();

        verify(expireOrderUseCase, times(0)).expireByOrderId(any(), any());
    }

    @Test
    void due_orders_are_expired_with_clock_now() {
        OrderExpirationScheduler scheduler = new OrderExpirationScheduler(expireOrderUseCase, orderRepository, fixedClock);
        LocalDateTime expectedNow = LocalDateTime.of(2026, 3, 15, 10, 0);
        Order first = createOrder(1L, null);
        Order second = createOrder(2L, null);
        Slice<Order> slice = new SliceImpl<>(List.of(first, second));
        when(orderRepository.findAllByStatusAndExpiresAtLessThanEqual(eq(OrderState.PENDING), eq(expectedNow), any()))
                .thenReturn(slice);

        scheduler.expirePendingOrders();

        verify(expireOrderUseCase).expireByOrderId(1L, expectedNow);
        verify(expireOrderUseCase).expireByOrderId(2L, expectedNow);
        verify(orderRepository, times(1)).findAllByStatusAndExpiresAtLessThanEqual(eq(OrderState.PENDING), eq(expectedNow), any());
    }

    @Test
    void batch_stops_when_all_expirations_fail() {
        OrderExpirationScheduler scheduler = new OrderExpirationScheduler(expireOrderUseCase, orderRepository, fixedClock);
        LocalDateTime expectedNow = LocalDateTime.of(2026, 3, 15, 10, 0);
        Order order = createOrder(1L, "order-1");
        Slice<Order> slice = new SliceImpl<>(List.of(order));
        when(orderRepository.findAllByStatusAndExpiresAtLessThanEqual(eq(OrderState.PENDING), eq(expectedNow), any()))
                .thenReturn(slice);
        doThrow(new RuntimeException("boom")).when(expireOrderUseCase).expireByOrderId(1L, expectedNow);

        scheduler.expirePendingOrders();

        verify(expireOrderUseCase).expireByOrderId(1L, expectedNow);
        verify(orderRepository, times(1)).findAllByStatusAndExpiresAtLessThanEqual(eq(OrderState.PENDING), eq(expectedNow), any());
    }

    private Order createOrder(final Long id, final String orderKey) {
        Order order = mock(Order.class);
        when(order.getId()).thenReturn(id);
        if (orderKey != null) {
            when(order.getOrderKey()).thenReturn(orderKey);
        }
        return order;
    }
}
