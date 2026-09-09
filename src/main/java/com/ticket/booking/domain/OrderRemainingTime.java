package com.ticket.booking.domain;

import com.ticket.booking.domain.OrderState;
import java.time.Duration;
import java.time.LocalDateTime;

public final class OrderRemainingTime {

    private OrderRemainingTime() {
    }

    public static long seconds(
            final OrderState status,
            final LocalDateTime expiresAt,
            final LocalDateTime now
    ) {
        if (status != OrderState.PENDING) {
            return 0L;
        }
        return Math.max(0L, Duration.between(now, expiresAt).getSeconds());
    }
}
