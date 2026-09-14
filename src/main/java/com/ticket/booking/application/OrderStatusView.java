package com.ticket.booking.application;

import java.time.LocalDateTime;

import com.ticket.booking.domain.order.OrderState;

public record OrderStatusView(String orderKey, OrderState status, LocalDateTime expiresAt) {}
