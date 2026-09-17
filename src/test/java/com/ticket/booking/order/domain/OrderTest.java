package com.ticket.booking.order.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

@SuppressWarnings("NonAsciiCharacters")
class OrderTest {
    @Test
    void 주문을_생성하면_pending_상태로_초기화된다() {
        LocalDateTime expiresAt = LocalDateTime.of(2026, 3, 15, 12, 30);

        Order order = createOrder(expiresAt);

        assertThat(order.getMemberId()).isEqualTo(1L);
        assertThat(order.getPerformanceId()).isEqualTo(10L);
        assertThat(order.getOrderKey()).isEqualTo("order-key");
        assertThat(order.getHoldKey()).isEqualTo("hold-key");
        assertThat(order.getStatus()).isEqualTo(OrderState.PENDING);
        // 총액은 좌석을 추가하면서 누적된다. 좌석이 없는 시점의 주문은 0원이다.
        assertThat(order.getTotalAmount()).isEqualByComparingTo("0");
        assertThat(order.getExpiresAt()).isEqualTo(expiresAt);
    }

    @Test
    void pending_주문은_확정할_수_있다() {
        LocalDateTime now = LocalDateTime.of(2026, 3, 15, 12, 0);
        Order order = createOrder(now.plusMinutes(10));

        order.confirm(now);

        assertThat(order.getStatus()).isEqualTo(OrderState.CONFIRMED);
        assertThat(order.getConfirmedAt()).isEqualTo(now);
    }

    @Test
    void pending_주문은_만료시각이_지나면_만료할_수_있다() {
        LocalDateTime expiresAt = LocalDateTime.of(2026, 3, 15, 12, 0);
        LocalDateTime now = expiresAt.plusMinutes(1);
        Order order = createOrder(expiresAt);

        order.expire(now);

        assertThat(order.getStatus()).isEqualTo(OrderState.EXPIRED);
        assertThat(order.getExpiredAt()).isEqualTo(now);
    }

    @Test
    void pending_주문은_취소할_수_있다() {
        LocalDateTime now = LocalDateTime.of(2026, 3, 15, 12, 0);
        Order order = createOrder(now.plusMinutes(10));

        order.cancel(now);

        assertThat(order.getStatus()).isEqualTo(OrderState.CANCELED);
        assertThat(order.getCanceledAt()).isEqualTo(now);
    }

    @Test
    void pending이_아닌_주문은_다시_전이할_수_없다() {
        Order order = createOrder(LocalDateTime.of(2026, 3, 15, 12, 30));
        order.confirm(LocalDateTime.of(2026, 3, 15, 12, 0));

        assertThatThrownBy(() -> order.cancel(LocalDateTime.of(2026, 3, 15, 12, 5)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("currentStatus=CONFIRMED");
    }

    @Test
    void 만료시각과_같거나_지나면_만료_처리_대상이다() {
        LocalDateTime expiresAt = LocalDateTime.of(2026, 3, 15, 12, 30);
        Order order = createOrder(expiresAt);

        assertThat(order.isExpirable(expiresAt.minusSeconds(1))).isFalse();
        assertThat(order.isExpirable(expiresAt)).isTrue();
        assertThat(order.isExpirable(expiresAt.plusSeconds(1))).isTrue();
    }

    @Test
    void pending이_아니면_만료시각이_지나도_만료_처리_대상이_아니다() {
        LocalDateTime expiresAt = LocalDateTime.of(2026, 3, 15, 12, 30);
        Order order = createOrder(expiresAt);
        order.confirm(expiresAt.minusMinutes(1));

        assertThat(order.isExpirable(expiresAt.plusMinutes(1))).isFalse();
    }

    /** 만료는 시각으로만 정해진다 — 만료 경로로 불렸다는 사실만으로는 아직 유효한 주문을 EXPIRED로 바꿀 수 없다. */
    @Test
    void 만료시각_전에는_만료할_수_없다() {
        LocalDateTime expiresAt = LocalDateTime.of(2026, 3, 15, 12, 30);
        Order order = createOrder(expiresAt);

        assertThatThrownBy(() -> order.expire(expiresAt.minusSeconds(1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("만료 시각 전에는");
        assertThat(order.getStatus()).isEqualTo(OrderState.PENDING);
    }

    @Test
    void 만료시각과_같은_순간부터_만료할_수_있다() {
        LocalDateTime expiresAt = LocalDateTime.of(2026, 3, 15, 12, 30);
        Order order = createOrder(expiresAt);

        order.expire(expiresAt);

        assertThat(order.getStatus()).isEqualTo(OrderState.EXPIRED);
        assertThat(order.getExpiredAt()).isEqualTo(expiresAt);
    }

    /** 총액은 좌석 단가의 합으로만 정의된다 — 바깥에서 계산한 값을 받지 않으므로 어긋날 경로가 없다. */
    @Test
    void 총액은_추가한_좌석_단가의_합이다() {
        Order order = createOrder(LocalDateTime.of(2026, 3, 15, 12, 30));

        assertThat(order.getTotalAmount()).isEqualByComparingTo("0");

        order.addOrderSeat(501L, 42L, new BigDecimal("12000"), "R", "R석", "1F 가구역 A열 1번");
        order.addOrderSeat(502L, 43L, new BigDecimal("15000"), "S", "S석", "1F 가구역 A열 2번");

        assertThat(order.getTotalAmount()).isEqualByComparingTo("27000");
    }

    @Test
    void 종료된_주문에는_좌석을_추가할_수_없다() {
        LocalDateTime expiresAt = LocalDateTime.of(2026, 3, 15, 12, 30);
        Order order = createOrder(expiresAt);
        order.cancel(expiresAt.minusMinutes(1));

        assertThatThrownBy(
                        () ->
                                order.addOrderSeat(
                                        501L,
                                        42L,
                                        new BigDecimal("12000"),
                                        "R",
                                        "R석",
                                        "1F 가구역 A열 1번"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("currentStatus=CANCELED");
        assertThat(order.getTotalAmount()).isEqualByComparingTo("0");
    }

    private Order createOrder(final LocalDateTime expiresAt) {
        return new Order(
                1L,
                10L,
                "order-key",
                "hold-key",
                expiresAt,
                "show-title",
                expiresAt.minusDays(1),
                "venue-name");
    }
}
