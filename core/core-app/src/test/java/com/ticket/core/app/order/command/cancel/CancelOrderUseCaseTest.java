package com.ticket.core.app.order.command.cancel;

import com.ticket.core.domain.member.query.MemberFinder;
import com.ticket.core.domain.order.command.OrderTerminationService;
import com.ticket.core.domain.order.model.Order;
import com.ticket.core.domain.order.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
class CancelOrderUseCaseTest {

    @Mock
    private MemberFinder memberFinder;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderTerminationService orderTerminationService;

    private final Clock fixedClock = Clock.fixed(Instant.parse("2026-03-15T01:00:00Z"), ZoneId.of("Asia/Seoul"));

    @Test
    void 취소_요청이면_고정_Clock_시간으로_주문을_취소한다() {
        final CancelOrderUseCase useCase = new CancelOrderUseCase(
                memberFinder,
                orderRepository,
                orderTerminationService,
                fixedClock
        );
        final Order order = createOrder(10L, 100L, "hold-key");
        final LocalDateTime expectedNow = LocalDateTime.of(2026, 3, 15, 10, 0);
        when(orderRepository.findByOrderKeyAndMemberIdForUpdate("order-key", 1L)).thenReturn(java.util.Optional.of(order));

        useCase.execute(new CancelOrderUseCase.Input("order-key", 1L));

        verify(memberFinder).findActiveMemberById(1L);
        verify(orderRepository).findByOrderKeyAndMemberIdForUpdate("order-key", 1L);
        verify(orderTerminationService).cancel(order, expectedNow);
    }

    private Order createOrder(final Long id, final Long performanceId, final String holdKey) {
        final Order order = new Order(1L, performanceId, "order-key", holdKey, BigDecimal.TEN, LocalDateTime.now().plusMinutes(5));
        ReflectionTestUtils.setField(order, "id", id);
        return order;
    }
}
