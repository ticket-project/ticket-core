package com.ticket.core.app.order.query.model;

import com.ticket.core.domain.order.model.OrderState;

import java.time.LocalDateTime;

public record OrderStatusView(
        String orderKey,
        OrderState status,
        LocalDateTime expiresAt
) {
}
