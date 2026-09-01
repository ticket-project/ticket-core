package com.ticket.core.domain.order.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SuppressWarnings("NonAsciiCharacters")
class OrderTest {

    @Test
    void 주문을_생성하면_pending_상태로_초기화된다() {
        //given
        LocalDateTime expiresAt = LocalDateTime.of(2026, 3, 15, 12, 30);

        //when
        Order order = createOrder(expiresAt);

        //then
        assertThat(order.getMemberId()).isEqualTo(1L);
        assertThat(order.getPerformanceId()).isEqualTo(10L);
        assertThat(order.getOrderKey()).isEqualTo("order-key");
        assertThat(order.getHoldKey()).isEqualTo("hold-key");
        assertThat(order.getStatus()).isEqualTo(OrderState.PENDING);
        assertThat(order.getTotalAmount()).isEqualByComparingTo("15000");
        assertThat(order.getExpiresAt()).isEqualTo(expiresAt);
    }

    @Test
    void pending_주문은_확정할_수_있다() {
        //given
        LocalDateTime now = LocalDateTime.of(2026, 3, 15, 12, 0);
        Order order = createOrder(now.plusMinutes(10));

        //when
        order.confirm(now);

        //then
        assertThat(order.getStatus()).isEqualTo(OrderState.CONFIRMED);
        assertThat(order.getConfirmedAt()).isEqualTo(now);
    }

    @Test
    void pending_주문은_만료할_수_있다() {
        //given
        LocalDateTime now = LocalDateTime.of(2026, 3, 15, 12, 0);
        Order order = createOrder(now.plusMinutes(10));

        //when
        order.expire(now);

        //then
        assertThat(order.getStatus()).isEqualTo(OrderState.EXPIRED);
        assertThat(order.getExpiredAt()).isEqualTo(now);
    }

    @Test
    void pending_주문은_취소할_수_있다() {
        //given
        LocalDateTime now = LocalDateTime.of(2026, 3, 15, 12, 0);
        Order order = createOrder(now.plusMinutes(10));

        //when
        order.cancel(now);

        //then
        assertThat(order.getStatus()).isEqualTo(OrderState.CANCELED);
        assertThat(order.getCanceledAt()).isEqualTo(now);
    }

    @Test
    void pending_주문은_결제실패로_전이할_수_있다() {
        //given
        LocalDateTime now = LocalDateTime.of(2026, 3, 15, 12, 0);
        Order order = createOrder(now.plusMinutes(10));

        //when
        order.failPayment(now);

        //then
        assertThat(order.getStatus()).isEqualTo(OrderState.PAYMENT_FAILED);
        assertThat(order.getPaymentFailedAt()).isEqualTo(now);
    }

    @Test
    void pending이_아닌_주문은_다시_전이할_수_없다() {
        //given
        Order order = createOrder(LocalDateTime.of(2026, 3, 15, 12, 30));
        order.confirm(LocalDateTime.of(2026, 3, 15, 12, 0));

        //when
        //then
        assertThatThrownBy(() -> order.cancel(LocalDateTime.of(2026, 3, 15, 12, 5)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("currentStatus=CONFIRMED");
    }

    @Test
    void 만료시각과_같거나_지난_pending_주문은_만료된_것으로_본다() {
        //given
        //when
        LocalDateTime expiresAt = LocalDateTime.of(2026, 3, 15, 12, 30);
        Order order = createOrder(expiresAt);

        //then
        assertThat(order.isExpired(expiresAt.minusSeconds(1))).isFalse();
        assertThat(order.isExpired(expiresAt)).isTrue();
        assertThat(order.isExpired(expiresAt.plusSeconds(1))).isTrue();
    }

    @Test
    void pending이_아닌_주문은_만료시각이_지나도_isExpired가_false다() {
        //given
        //when
        LocalDateTime expiresAt = LocalDateTime.of(2026, 3, 15, 12, 30);
        Order order = createOrder(expiresAt);
        order.confirm(expiresAt.minusMinutes(1));

        //then
        assertThat(order.isExpired(expiresAt.plusMinutes(1))).isFalse();
    }

    private Order createOrder(final LocalDateTime expiresAt) {
        return new Order(1L, 10L, "order-key", "hold-key", BigDecimal.valueOf(15000), expiresAt);
    }
}
