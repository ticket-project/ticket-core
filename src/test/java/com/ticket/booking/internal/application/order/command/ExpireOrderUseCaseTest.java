package com.ticket.booking.internal.application.order.command;

import com.ticket.booking.internal.application.order.command.ExpireOrderUseCase;
import com.ticket.booking.internal.application.order.command.OrderTerminationService;
import com.ticket.booking.internal.domain.order.model.Order;
import com.ticket.booking.internal.domain.order.model.OrderState;
import com.ticket.booking.internal.domain.order.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
class ExpireOrderUseCaseTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderTerminationService orderTerminationService;

    @InjectMocks
    private ExpireOrderUseCase useCase;

    @Test
    void orderId로_조회한_주문이_없으면_noop이다() {
        when(orderRepository.findByIdAndStatusForUpdate(10L, OrderState.PENDING)).thenReturn(java.util.Optional.empty());

        useCase.expireByOrderId(10L, LocalDateTime.of(2026, 3, 15, 10, 0));

        verify(orderRepository).findByIdAndStatusForUpdate(10L, OrderState.PENDING);
        verifyNoInteractions(orderTerminationService);
    }

    @Test
    void orderId로_조회한_주문이_있으면_만료를_처리한다() {
        final Order order = createOrder(10L, 100L, "hold-key");
        final LocalDateTime now = LocalDateTime.of(2026, 3, 15, 10, 0);
        when(orderRepository.findByIdAndStatusForUpdate(10L, OrderState.PENDING)).thenReturn(java.util.Optional.of(order));

        useCase.expireByOrderId(10L, now);

        verify(orderTerminationService).expire(order, now);
    }

    @Test
    void holdKey로_조회한_주문이_있으면_만료를_처리한다() {
        final Order order = createOrder(10L, 100L, "hold-key");
        final LocalDateTime now = LocalDateTime.of(2026, 3, 15, 10, 0);
        when(orderRepository.findByHoldKeyAndStatusForUpdate("hold-key", OrderState.PENDING)).thenReturn(java.util.Optional.of(order));

        useCase.expireByHoldKey("hold-key", now);

        verify(orderTerminationService).expire(order, now);
    }

    @Test
    void duplicate_hold_expiration_publishes_only_once() {
        final Order order = createOrder(10L, 100L, "hold-key");
        when(orderRepository.findByHoldKeyAndStatusForUpdate("hold-key", OrderState.PENDING))
                .thenReturn(java.util.Optional.of(order), java.util.Optional.empty());

        useCase.expireByHoldKey("hold-key", LocalDateTime.of(2026, 3, 15, 10, 0));
        useCase.expireByHoldKey("hold-key", LocalDateTime.of(2026, 3, 15, 10, 0));

        verify(orderRepository, times(2)).findByHoldKeyAndStatusForUpdate("hold-key", OrderState.PENDING);
        verify(orderTerminationService).expire(order, LocalDateTime.of(2026, 3, 15, 10, 0));
    }

    private Order createOrder(final Long id, final Long performanceId, final String holdKey) {
        final Order order = new Order(1L, performanceId, "order-key", holdKey, BigDecimal.TEN, LocalDateTime.now().plusMinutes(5), "show-title", LocalDateTime.now().plusDays(1), "venue-name");
        ReflectionTestUtils.setField(order, "id", id);
        return order;
    }
}
