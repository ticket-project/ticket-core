package com.ticket.core.infra.order.outbox.release;

import com.ticket.core.support.exception.CoreException;
import com.ticket.core.support.exception.ErrorType;

import com.ticket.core.app.lock.LockKey;
import com.ticket.core.app.lock.LockManager;
import com.ticket.core.app.lock.LockOptions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class HoldReleaseOutboxRelay {

    private static final int BATCH_SIZE = 100;
    private static final List<HoldReleaseOutboxStatus> RETRYABLE_STATUSES = List.of(
            HoldReleaseOutboxStatus.PENDING,
            HoldReleaseOutboxStatus.FAILED
    );

    private final HoldReleaseOutboxRepository holdReleaseOutboxRepository;
    private final HoldReleaseOutboxExecutor holdReleaseOutboxExecutor;
    /** 인스턴스가 여럿이어도 보정 배치는 한 번만 돈다. */
    private static final LockOptions BATCH_LOCK = LockOptions.defaults()
            .withLeaseTime(Duration.ofSeconds(60))
            .withFailureMessage("hold release outbox batch is already being processed.");

    private final LockManager lockManager;
    private final Clock clock;

    public void relayPending() {
        lockManager.withLock(
                java.util.List.of(LockKey.holdReleaseOutboxBatch()),
                BATCH_LOCK,
                this::relayPendingLocked
        );
    }

    private void relayPendingLocked() {
        while (true) {
            final Slice<HoldReleaseOutbox> dueOutboxes = holdReleaseOutboxRepository.findAllByStatusInAndNextAttemptAtLessThanEqual(
                    RETRYABLE_STATUSES,
                    LocalDateTime.now(clock),
                    PageRequest.of(0, BATCH_SIZE, Sort.by(Sort.Direction.ASC, "id"))
            );
            if (!dueOutboxes.hasContent()) {
                return;
            }

            boolean processStartFailed = false;
            for (final HoldReleaseOutbox outbox : dueOutboxes.getContent()) {
                try {
                    holdReleaseOutboxExecutor.process(outbox.getId(), LocalDateTime.now(clock));
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
            log.debug("hold release outbox가 이미 처리 중입니다. outboxId={}", outboxId);
            return;
        }
        log.warn("hold release outbox 실행을 시작하지 못했습니다. outboxId={}", outboxId, exception);
    }

    private boolean isExpectedLockContention(final RuntimeException exception) {
        return exception instanceof CoreException coreException
                && coreException.getErrorType() == ErrorType.HOLD_BUSY;
    }
}
