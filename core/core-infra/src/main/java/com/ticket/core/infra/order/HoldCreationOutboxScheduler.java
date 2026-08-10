package com.ticket.core.infra.order;

import com.ticket.core.domain.order.command.create.HoldCreationOutbox;
import com.ticket.core.domain.order.command.create.HoldCreationOutboxRepository;
import com.ticket.core.domain.order.command.create.HoldCreationOutboxStatus;
import com.ticket.core.support.exception.CoreException;
import com.ticket.core.support.exception.ErrorType;
import com.ticket.core.support.lock.DistributedLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class HoldCreationOutboxScheduler {

    private static final int BATCH_SIZE = 100;
    private static final List<HoldCreationOutboxStatus> RETRYABLE_STATUSES = List.of(
            HoldCreationOutboxStatus.PENDING,
            HoldCreationOutboxStatus.FAILED
    );

    private final HoldCreationOutboxRepository holdCreationOutboxRepository;
    private final HoldCreationOutboxExecutor holdCreationOutboxExecutor;
    private final Clock clock;

    @Scheduled(fixedDelayString = "120000")
    @DistributedLock(
            prefix = "hold-creation-outbox",
            dynamicKey = "'batch'",
            leaseTime = 60000L,
            message = "hold creation outbox batch is already being processed."
    )
    public void processPendingHoldCreations() {
        while (true) {
            final Slice<HoldCreationOutbox> dueOutboxes = holdCreationOutboxRepository
                    .findAllByStatusInAndNextAttemptAtLessThanEqual(
                            RETRYABLE_STATUSES,
                            LocalDateTime.now(clock),
                            PageRequest.of(0, BATCH_SIZE, Sort.by(Sort.Direction.ASC, "id"))
                    );
            if (!dueOutboxes.hasContent()) {
                return;
            }

            boolean processStartFailed = false;
            for (final HoldCreationOutbox outbox : dueOutboxes.getContent()) {
                try {
                    holdCreationOutboxExecutor.process(outbox.getId(), LocalDateTime.now(clock));
                } catch (final RuntimeException e) {
                    processStartFailed = true;
                    logProcessStartFailure(outbox.getId(), e);
                }
            }

            if (processStartFailed || dueOutboxes.getNumberOfElements() < BATCH_SIZE) {
                return;
            }
        }
    }

    private void logProcessStartFailure(final Long outboxId, final RuntimeException exception) {
        if (isExpectedLockContention(exception)) {
            log.debug("hold creation outbox is already being processed. outboxId={}", outboxId);
            return;
        }
        log.warn("hold creation outbox could not start. outboxId={}", outboxId, exception);
    }

    private boolean isExpectedLockContention(final RuntimeException exception) {
        return exception instanceof CoreException coreException
                && coreException.getErrorType() == ErrorType.HOLD_BUSY;
    }
}
