package com.ticket.booking.internal.application.order.command;

import com.ticket.identity.internal.domain.member.model.Member;
import com.ticket.booking.internal.application.order.command.CancelOrderUseCase;
import com.ticket.identity.internal.domain.member.repository.MemberRepository;
import com.ticket.booking.internal.application.order.command.OrderTerminationService;
import com.ticket.booking.internal.domain.order.model.Order;
import com.ticket.booking.internal.domain.order.repository.OrderRepository;
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
import static org.mockito.Mockito.mock;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
class CancelOrderUseCaseTest {

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderTerminationService orderTerminationService;

    private final Clock fixedClock = Clock.fixed(Instant.parse("2026-03-15T01:00:00Z"), ZoneId.of("Asia/Seoul"));

    @Test
    void 취소_요청이면_고정_Clock_시간으로_주문을_취소한다() {
        final CancelOrderUseCase useCase = new CancelOrderUseCase(
                memberRepository,
                orderRepository,
                orderTerminationService,
                fixedClock
        );
        final Order order = createOrder(10L, 100L, "hold-key");
        final LocalDateTime expectedNow = LocalDateTime.of(2026, 3, 15, 10, 0);
        when(memberRepository.findActiveById(1L)).thenReturn(Optional.of(mock(Member.class)));
        when(orderRepository.findByOrderKeyAndMemberIdForUpdate("order-key", 1L)).thenReturn(java.util.Optional.of(order));

        useCase.execute(new CancelOrderUseCase.Input("order-key", 1L));

        verify(memberRepository).findActiveById(1L);
        verify(orderRepository).findByOrderKeyAndMemberIdForUpdate("order-key", 1L);
        verify(orderTerminationService).cancel(order, expectedNow);
    }

    private Order createOrder(final Long id, final Long performanceId, final String holdKey) {
        final Order order = new Order(1L, performanceId, "order-key", holdKey, BigDecimal.TEN, LocalDateTime.now().plusMinutes(5));
        ReflectionTestUtils.setField(order, "id", id);
        return order;
    }
}
