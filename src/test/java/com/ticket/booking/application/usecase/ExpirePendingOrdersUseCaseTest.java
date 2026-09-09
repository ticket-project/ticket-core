package com.ticket.booking.application.usecase;

import com.ticket.booking.domain.Order;
import com.ticket.booking.domain.OrderState;
import com.ticket.booking.domain.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("NonAsciiCharacters")
@ExtendWith(MockitoExtension.class)
class ExpirePendingOrdersUseCaseTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ExpireOrderUseCase expireOrderUseCase;

    private final Clock fixedClock = Clock.fixed(Instant.parse("2026-03-15T01:00:00Z"), ZoneId.of("Asia/Seoul"));

    @Test
    void no_expired_orders_returns_immediately() {
        ExpirePendingOrdersUseCase useCase = new ExpirePendingOrdersUseCase(orderRepository, expireOrderUseCase, fixedClock);
        LocalDateTime expectedNow = LocalDateTime.of(2026, 3, 15, 10, 0);
        when(orderRepository.findExpirable(eq(OrderState.PENDING), eq(expectedNow), anyInt()))
                .thenReturn(List.<Order>of());

        useCase.execute();

        verify(expireOrderUseCase, times(0)).expireByOrderId(any(), any());
    }

    @Test
    void due_orders_are_expired_with_clock_now() {
        ExpirePendingOrdersUseCase useCase = new ExpirePendingOrdersUseCase(orderRepository, expireOrderUseCase, fixedClock);
        LocalDateTime expectedNow = LocalDateTime.of(2026, 3, 15, 10, 0);
        Order first = createOrder(1L, null);
        Order second = createOrder(2L, null);
        List<Order> slice = List.of(first, second);
        when(orderRepository.findExpirable(eq(OrderState.PENDING), eq(expectedNow), anyInt()))
                .thenReturn(slice);

        useCase.execute();

        verify(expireOrderUseCase).expireByOrderId(1L, expectedNow);
        verify(expireOrderUseCase).expireByOrderId(2L, expectedNow);
        verify(orderRepository, times(1)).findExpirable(eq(OrderState.PENDING), eq(expectedNow), anyInt());
    }

    @Test
    void batch_stops_when_all_expirations_fail() {
        ExpirePendingOrdersUseCase useCase = new ExpirePendingOrdersUseCase(orderRepository, expireOrderUseCase, fixedClock);
        LocalDateTime expectedNow = LocalDateTime.of(2026, 3, 15, 10, 0);
        Order order = createOrder(1L, "order-1");
        List<Order> slice = List.of(order);
        when(orderRepository.findExpirable(eq(OrderState.PENDING), eq(expectedNow), anyInt()))
                .thenReturn(slice);
        doThrow(new RuntimeException("boom")).when(expireOrderUseCase).expireByOrderId(1L, expectedNow);

        useCase.execute();

        verify(expireOrderUseCase).expireByOrderId(1L, expectedNow);
        verify(orderRepository, times(1)).findExpirable(eq(OrderState.PENDING), eq(expectedNow), anyInt());
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
