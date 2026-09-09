package com.ticket.booking.application;

import com.ticket.booking.domain.OrderState;

import java.time.LocalDateTime;

public record OrderStatusView(
        String orderKey,
        OrderState status,
        LocalDateTime expiresAt
) {
}
