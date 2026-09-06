package com.ticket.booking.domain.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.ticket.booking.domain.order.model.OrderState;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class OrderRemainingTimeTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 12, 12, 0);

    @Test
    void pending_order_returns_whole_non_negative_seconds() {
        assertThat(OrderRemainingTime.seconds(OrderState.PENDING, NOW.plusMinutes(10), NOW))
                .isEqualTo(600L);
        assertThat(OrderRemainingTime.seconds(OrderState.PENDING, NOW.plusNanos(999_999_999L), NOW))
                .isZero();
    }

    @Test
    void exact_expiration_and_expired_order_return_zero() {
        assertThat(OrderRemainingTime.seconds(OrderState.PENDING, NOW, NOW)).isZero();
        assertThat(OrderRemainingTime.seconds(OrderState.PENDING, NOW.minusSeconds(1), NOW)).isZero();
    }

    @Test
    void non_pending_order_returns_zero_even_when_expiration_is_in_the_future() {
        assertThat(OrderRemainingTime.seconds(OrderState.CONFIRMED, NOW.plusMinutes(10), NOW)).isZero();
        assertThat(OrderRemainingTime.seconds(OrderState.CANCELED, NOW.plusMinutes(10), NOW)).isZero();
        assertThat(OrderRemainingTime.seconds(OrderState.EXPIRED, NOW.plusMinutes(10), NOW)).isZero();
    }
}
