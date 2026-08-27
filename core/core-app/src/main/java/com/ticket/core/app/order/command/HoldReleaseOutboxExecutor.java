package com.ticket.core.app.order.command;

import com.ticket.core.domain.order.command.release.HoldReleaseTask;
import com.ticket.core.app.lock.LockKey;
import com.ticket.core.app.lock.LockManager;
import com.ticket.core.app.lock.LockOptions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class HoldReleaseOutboxExecutor {

    private static final Duration RETRY_DELAY = Duration.ofSeconds(30);

    /** 같은 outbox를 즉시 작업자와 스케줄러가 동시에 집어도 외부 부수효과를 한 번만 실행한다. */
    private static final LockOptions ENTRY_LOCK = LockOptions.waiting(Duration.ofMillis(100))
            .withoutWarningOnFailure()
            .withFailureMessage("hold release outbox가 이미 처리 중입니다.");

    private final LockManager lockManager;
    private final HoldReleaseOutboxTransactionService transactionService;
    private final HoldReleaseTaskProcessor taskProcessor;

    public void process(final Long outboxId, final LocalDateTime now) {
        lockManager.withLock(
                java.util.List.of(LockKey.holdReleaseOutboxEntry(outboxId)),
                ENTRY_LOCK,
                () -> processLocked(outboxId, now)
        );
    }

    private void processLocked(final Long outboxId, final LocalDateTime now) {
        final HoldReleaseTask task = transactionService.load(outboxId);
        if (task == null) {
            return;
        }

        try {
            taskProcessor.process(outboxId, task, now);
            transactionService.markCompleted(outboxId, now);
        } catch (final RuntimeException e) {
            transactionService.scheduleRetry(outboxId, now.plus(RETRY_DELAY), e.getMessage());
            log.error("hold release outbox 처리 실패: outboxId={}, holdKey={}", outboxId, task.holdKey(), e);
        }
    }
}
