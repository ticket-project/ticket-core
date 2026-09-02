package com.ticket.booking.internal.domain.order.command.create;

import com.ticket.booking.internal.domain.order.model.Order;

public record PendingOrderCreationResult(Order order, Long postCommitOutboxId) {
}
