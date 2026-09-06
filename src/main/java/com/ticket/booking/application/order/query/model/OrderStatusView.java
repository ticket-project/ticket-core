package com.ticket.booking.application.order.query.model;

import com.ticket.booking.domain.order.model.OrderState;

import java.time.LocalDateTime;

public record OrderStatusView(
        String orderKey,
        OrderState status,
        LocalDateTime expiresAt
) {
}
