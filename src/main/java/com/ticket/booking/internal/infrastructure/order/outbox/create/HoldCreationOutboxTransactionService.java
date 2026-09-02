package com.ticket.booking.internal.infrastructure.order.outbox.create;

import com.ticket.booking.internal.infrastructure.order.outbox.create.HoldCreationOutboxRepository;
import com.ticket.booking.internal.infrastructure.order.outbox.create.HoldCreationOutbox;
import com.ticket.booking.internal.domain.hold.model.Hold;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class HoldCreationOutboxTransactionService {

    private final HoldCreationOutboxRepository holdCreationOutboxRepository;

    @Transactional(readOnly = true)
    public Hold load(final Long outboxId) {
        return holdCreationOutboxRepository.findById(outboxId)
                .filter(outbox -> !outbox.isCompleted())
                .map(HoldCreationOutbox::toHold)
                .orElse(null);
    }

    @Transactional
    public void markCompleted(final Long outboxId, final LocalDateTime completedAt) {
        final HoldCreationOutbox outbox = findForUpdate(outboxId);
        if (outbox == null || outbox.isCompleted()) {
            return;
        }
        outbox.markCompleted(completedAt);
    }

    @Transactional
    public void scheduleRetry(final Long outboxId, final LocalDateTime nextAttemptAt, final String errorMessage) {
        final HoldCreationOutbox outbox = findForUpdate(outboxId);
        if (outbox == null || outbox.isCompleted()) {
            return;
        }
        outbox.scheduleRetry(nextAttemptAt, errorMessage);
    }

    private HoldCreationOutbox findForUpdate(final Long outboxId) {
        return holdCreationOutboxRepository.findByIdForUpdate(outboxId).orElse(null);
    }
}
