package com.ticket.booking.order.application;

import com.ticket.booking.order.domain.Order;
import com.ticket.booking.order.domain.OrderRepository;
import com.ticket.booking.order.domain.OrderState;
import com.ticket.booking.order.exception.OrderNotOwnedException;
import com.ticket.booking.order.exception.OrderNotPendingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
class CancelOrderTransactionServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderTerminationService orderTerminationService;

    private final Clock fixedClock = Clock.fixed(Instant.parse("2026-03-15T01:00:00Z"), ZoneId.of("Asia/Seoul"));

    private CancelOrderTransactionService service;

    @BeforeEach
    void setUp() {
        service = new CancelOrderTransactionService(orderRepository, orderTerminationService, fixedClock);
    }

    @Test
    void cancel은_짧은_쓰기_트랜잭션에서_실행된다() throws NoSuchMethodException {
        Transactional transactional = CancelOrderTransactionService.class
                .getDeclaredMethod("cancel", String.class, Long.class)
                .getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
    }

    @Test
    void 취소_요청이면_고정_Clock_시간으로_주문을_취소한다() {
        final Order order = createOrder(10L, 100L, "hold-key");
        final LocalDateTime expectedNow = LocalDateTime.of(2026, 3, 15, 10, 0);
        when(orderRepository.findByOrderKeyAndMemberIdForUpdate("order-key", 1L)).thenReturn(Optional.of(order));

        service.cancel("order-key", 1L);

        verify(orderRepository).findByOrderKeyAndMemberIdForUpdate("order-key", 1L);
        verify(orderTerminationService).cancel(order, expectedNow);
    }

    @Test
    void 본인_주문이_없으면_권한예외를_던진다() {
        when(orderRepository.findByOrderKeyAndMemberIdForUpdate("missing", 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cancel("missing", 1L))
                .isInstanceOf(OrderNotOwnedException.class)
                .hasFieldOrPropertyWithValue("orderKey", "missing")
                .hasFieldOrPropertyWithValue("memberId", 1L);
    }

    @Test
    void 결제_대기가_아닌_주문은_취소를_거절하고_실제_상태를_담는다() {
        final Order order = createOrder(10L, 100L, "hold-key");
        ReflectionTestUtils.setField(order, "status", OrderState.CANCELED);
        when(orderRepository.findByOrderKeyAndMemberIdForUpdate("order-key", 1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.cancel("order-key", 1L))
                .isInstanceOf(OrderNotPendingException.class)
                .hasFieldOrPropertyWithValue("currentStatus", OrderState.CANCELED);

        verifyNoInteractions(orderTerminationService);
    }

    private Order createOrder(final Long id, final Long performanceId, final String holdKey) {
        final Order order = new Order(1L, performanceId, "order-key", holdKey, BigDecimal.TEN, LocalDateTime.now().plusMinutes(5), "show-title", LocalDateTime.now().plusDays(1), "venue-name");
        ReflectionTestUtils.setField(order, "id", id);
        return order;
    }
}
