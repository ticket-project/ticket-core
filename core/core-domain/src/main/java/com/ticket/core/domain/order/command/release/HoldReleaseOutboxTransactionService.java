package com.ticket.core.domain.order.command.release;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class HoldReleaseOutboxTransactionService {

    private final HoldReleaseOutboxRepository holdReleaseOutboxRepository;

    @Transactional(readOnly = true)
    public HoldReleaseTask load(final Long outboxId) {
        return holdReleaseOutboxRepository.findById(outboxId)
                .filter(outbox -> !outbox.isCompleted())
                .map(outbox -> new HoldReleaseTask(
                        outbox.getPerformanceId(),
                        outbox.getHoldKey(),
                        outbox.seatIds(),
                        outbox.isHoldReleased(),
                        outbox.getReason()
                ))
                .orElse(null);
    }

    @Transactional
    public void markHoldReleased(final Long outboxId, final LocalDateTime releasedAt) {
        final HoldReleaseOutbox outbox = findForUpdate(outboxId);
        if (outbox == null || outbox.isCompleted()) {
            return;
        }
        outbox.markHoldReleased(releasedAt);
    }

    @Transactional
    public void markCompleted(final Long outboxId, final LocalDateTime completedAt) {
        final HoldReleaseOutbox outbox = findForUpdate(outboxId);
        if (outbox == null || outbox.isCompleted()) {
            return;
        }
        outbox.markCompleted(completedAt);
    }

    @Transactional
    public void scheduleRetry(
            final Long outboxId,
            final LocalDateTime nextAttemptAt,
            final String errorMessage
    ) {
        final HoldReleaseOutbox outbox = findForUpdate(outboxId);
        if (outbox == null || outbox.isCompleted()) {
            return;
        }
        outbox.scheduleRetry(nextAttemptAt, errorMessage);
    }

    private HoldReleaseOutbox findForUpdate(final Long outboxId) {
        return holdReleaseOutboxRepository.findByIdForUpdate(outboxId).orElse(null);
    }
}
