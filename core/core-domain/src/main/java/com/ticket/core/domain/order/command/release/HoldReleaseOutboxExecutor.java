package com.ticket.core.domain.order.command.release;

import com.ticket.core.support.lock.DistributedLock;
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

    private final HoldReleaseOutboxTransactionService transactionService;
    private final HoldReleaseTaskProcessor taskProcessor;

    @DistributedLock(
            prefix = "hold-release-outbox-entry",
            dynamicKey = "#outboxId",
            waitTime = 100L,
            warnOnFailure = false,
            message = "hold release outbox가 이미 처리 중입니다."
    )
    public void process(final Long outboxId, final LocalDateTime now) {
        final HoldReleaseTask task = transactionService.load(outboxId);
        if (task == null) {
            return;
        }

        try {
            taskProcessor.process(task);
            transactionService.markCompleted(outboxId, now);
        } catch (final RuntimeException e) {
            transactionService.scheduleRetry(outboxId, now.plus(RETRY_DELAY), e.getMessage());
            log.error("hold release outbox 처리 실패: outboxId={}, holdKey={}", outboxId, task.holdKey(), e);
        }
    }
}
