package com.ticket.core.infra.order;

import com.ticket.core.domain.hold.model.Hold;
import com.ticket.core.app.order.command.HoldCreationOutboxTransactionService;
import com.ticket.core.support.lock.DistributedLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class HoldCreationOutboxExecutor {

    private static final Duration RETRY_DELAY = Duration.ofSeconds(30);

    private final HoldCreationOutboxTransactionService transactionService;
    private final HoldCreationPostCommitProcessor processor;

    @DistributedLock(
            prefix = "hold-creation-outbox-entry",
            dynamicKey = "#outboxId",
            waitTime = 100L,
            warnOnFailure = false,
            message = "hold creation outbox is already being processed."
    )
    public void process(final Long outboxId, final LocalDateTime now) {
        final Hold hold = transactionService.load(outboxId);
        if (hold == null) {
            return;
        }

        try {
            processor.process(hold);
            transactionService.markCompleted(outboxId, now);
        } catch (final RuntimeException e) {
            transactionService.scheduleRetry(outboxId, now.plus(RETRY_DELAY), e.getMessage());
            log.error("hold creation outbox processing failed: outboxId={}, holdKey={}", outboxId, hold.holdKey(), e);
        }
    }
}
