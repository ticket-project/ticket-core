package com.ticket.booking.domain.order.command.create;

import com.ticket.booking.domain.order.model.Order;

public record PendingOrderCreationResult(Order order) {
}
