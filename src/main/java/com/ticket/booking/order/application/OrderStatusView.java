package com.ticket.booking.order.application;

import com.ticket.booking.order.domain.OrderState;

import java.time.LocalDateTime;

public record OrderStatusView(
        String orderKey,
        OrderState status,
        LocalDateTime expiresAt
) {
}
