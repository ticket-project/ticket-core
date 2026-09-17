package com.ticket.booking.order.query;

import java.time.LocalDateTime;

import com.ticket.booking.order.domain.OrderState;

public record OrderStatusView(String orderKey, OrderState status, LocalDateTime expiresAt) {}
