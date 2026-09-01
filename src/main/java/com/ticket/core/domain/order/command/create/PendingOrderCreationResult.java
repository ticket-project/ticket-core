package com.ticket.core.domain.order.command.create;

import com.ticket.core.domain.order.model.Order;

public record PendingOrderCreationResult(Order order, Long postCommitOutboxId) {
}
