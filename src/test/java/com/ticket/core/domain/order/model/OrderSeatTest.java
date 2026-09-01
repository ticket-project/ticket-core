package com.ticket.core.domain.order.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("NonAsciiCharacters")
class OrderSeatTest {

    @Test
    void 주문좌석을_생성하면_좌석_스냅샷_정보만_보관한다() {
        Order order = createOrder();

        OrderSeat orderSeat = new OrderSeat(order, 100L, 200L, BigDecimal.valueOf(12000));

        assertThat(orderSeat.getOrder()).isSameAs(order);
        assertThat(orderSeat.getPerformanceSeatId()).isEqualTo(100L);
        assertThat(orderSeat.getSeatId()).isEqualTo(200L);
        assertThat(orderSeat.getPrice()).isEqualByComparingTo("12000");
    }

    @Test
    void orderSeat는_상태_필드를_가지지_않는다() {
        assertThat(Arrays.stream(OrderSeat.class.getDeclaredFields())
                .map(field -> field.getName()))
                .doesNotContain("status");
    }

    @Test
    void orderSeat는_상태_전이_메서드를_가지지_않는다() {
        assertThat(Arrays.stream(OrderSeat.class.getDeclaredMethods())
                .map(method -> method.getName()))
                .doesNotContain("confirm", "expire", "cancel");
    }

    private Order createOrder() {
        return new Order(
                1L,
                10L,
                "order-key",
                "hold-key",
                BigDecimal.valueOf(12000),
                LocalDateTime.of(2026, 3, 15, 12, 30)
        );
    }
}
