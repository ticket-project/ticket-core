package com.ticket.core.domain.order.command.expire;

import com.ticket.core.domain.order.model.Order;
import com.ticket.core.domain.order.model.OrderSeat;
import com.ticket.core.domain.order.command.release.HoldReleaseRequestedEvent;
import com.ticket.core.domain.order.command.release.HoldReleaseOutboxWriter;
import com.ticket.core.domain.order.model.OrderState;
import com.ticket.core.domain.order.repository.OrderRepository;
import com.ticket.core.domain.order.repository.OrderSeatRepository;
import com.ticket.core.domain.order.OrderTerminationResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
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
    private OrderSeatRepository orderSeatRepository;

    @Mock
    private OrderExpirer orderExpirer;

    @Mock
    private HoldReleaseOutboxWriter holdReleaseOutboxWriter;

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @InjectMocks
    private ExpireOrderUseCase useCase;

    @Test
    void orderId로_조회한_주문이_없으면_noop이다() {
        when(orderRepository.findByIdAndStatusForUpdate(10L, OrderState.PENDING)).thenReturn(java.util.Optional.empty());

        useCase.expireByOrderId(10L, LocalDateTime.of(2026, 3, 15, 10, 0));

        verify(orderRepository).findByIdAndStatusForUpdate(10L, OrderState.PENDING);
        verifyNoInteractions(orderExpirer, holdReleaseOutboxWriter);
    }

    @Test
    void orderId로_조회한_주문이_있으면_만료_outbox를_적재한다() {
        final Order order = createOrder(10L, 100L, "hold-key");
        final OrderSeat orderSeat = mock(OrderSeat.class);
        final LocalDateTime now = LocalDateTime.of(2026, 3, 15, 10, 0);
        final OrderTerminationResult result = new OrderTerminationResult(100L, "hold-key", List.of(42L));
        when(orderRepository.findByIdAndStatusForUpdate(10L, OrderState.PENDING)).thenReturn(java.util.Optional.of(order));
        when(orderSeatRepository.findAllByOrder_IdOrderByIdAsc(10L)).thenReturn(List.of(orderSeat));
        when(orderExpirer.expire(eq(order), eq(List.of(orderSeat)), eq(now))).thenReturn(result);
        when(holdReleaseOutboxWriter.append(result)).thenReturn(99L);

        useCase.expireByOrderId(10L, now);

        verify(orderSeatRepository).findAllByOrder_IdOrderByIdAsc(10L);
        verify(orderExpirer).expire(order, List.of(orderSeat), now);
        verify(holdReleaseOutboxWriter).append(result);
        verify(applicationEventPublisher).publishEvent(new HoldReleaseRequestedEvent(99L));
    }

    @Test
    void holdKey로_조회한_주문이_있으면_만료_outbox를_적재한다() {
        final Order order = createOrder(10L, 100L, "hold-key");
        final OrderSeat orderSeat = mock(OrderSeat.class);
        final LocalDateTime now = LocalDateTime.of(2026, 3, 15, 10, 0);
        final OrderTerminationResult result = new OrderTerminationResult(100L, "hold-key", List.of(42L));
        when(orderRepository.findByHoldKeyAndStatusForUpdate("hold-key", OrderState.PENDING)).thenReturn(java.util.Optional.of(order));
        when(orderSeatRepository.findAllByOrder_IdOrderByIdAsc(10L)).thenReturn(List.of(orderSeat));
        when(orderExpirer.expire(eq(order), eq(List.of(orderSeat)), eq(now))).thenReturn(result);
        when(holdReleaseOutboxWriter.append(result)).thenReturn(99L);

        useCase.expireByHoldKey("hold-key", now);

        verify(orderSeatRepository).findAllByOrder_IdOrderByIdAsc(10L);
        verify(orderExpirer).expire(order, List.of(orderSeat), now);
        verify(holdReleaseOutboxWriter).append(result);
        verify(applicationEventPublisher).publishEvent(new HoldReleaseRequestedEvent(99L));
    }

    @Test
    void duplicate_hold_expiration_creates_one_outbox() {
        final Order order = createOrder(10L, 100L, "hold-key");
        final OrderSeat orderSeat = mock(OrderSeat.class);
        final LocalDateTime now = LocalDateTime.of(2026, 3, 15, 10, 0);
        final OrderTerminationResult result = new OrderTerminationResult(100L, "hold-key", List.of(42L));
        when(orderRepository.findByHoldKeyAndStatusForUpdate("hold-key", OrderState.PENDING))
                .thenReturn(java.util.Optional.of(order), java.util.Optional.empty());
        when(orderSeatRepository.findAllByOrder_IdOrderByIdAsc(10L)).thenReturn(List.of(orderSeat));
        when(orderExpirer.expire(order, List.of(orderSeat), now)).thenReturn(result);
        when(holdReleaseOutboxWriter.append(result)).thenReturn(99L);

        useCase.expireByHoldKey("hold-key", now);
        useCase.expireByHoldKey("hold-key", now);

        verify(orderRepository, times(2)).findByHoldKeyAndStatusForUpdate("hold-key", OrderState.PENDING);
        verify(orderExpirer).expire(order, List.of(orderSeat), now);
        verify(holdReleaseOutboxWriter).append(result);
        verify(applicationEventPublisher).publishEvent(new HoldReleaseRequestedEvent(99L));
    }

    private Order createOrder(final Long id, final Long performanceId, final String holdKey) {
        final Order order = new Order(1L, performanceId, "order-key", holdKey, BigDecimal.TEN, LocalDateTime.now().plusMinutes(5));
        ReflectionTestUtils.setField(order, "id", id);
        return order;
    }
}
