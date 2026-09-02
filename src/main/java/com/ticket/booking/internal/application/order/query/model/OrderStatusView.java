package com.ticket.booking.internal.application.order.query.model;

import com.ticket.booking.internal.domain.order.model.OrderState;

import java.time.LocalDateTime;

public record OrderStatusView(
        String orderKey,
        OrderState status,
        LocalDateTime expiresAt
) {
}
