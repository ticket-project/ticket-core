package com.ticket.core.infra.order;

import com.ticket.core.domain.hold.model.HoldSnapshot;
import com.ticket.core.domain.order.command.create.HoldCreationOutboxTransactionService;
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
        final HoldSnapshot snapshot = transactionService.load(outboxId);
        if (snapshot == null) {
            return;
        }

        try {
            processor.process(snapshot);
            transactionService.markCompleted(outboxId, now);
        } catch (final RuntimeException e) {
            transactionService.scheduleRetry(outboxId, now.plus(RETRY_DELAY), e.getMessage());
            log.error("hold creation outbox processing failed: outboxId={}, holdKey={}", outboxId, snapshot.holdKey(), e);
        }
    }
}
