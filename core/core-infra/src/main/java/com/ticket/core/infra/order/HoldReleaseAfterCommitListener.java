package com.ticket.core.infra.order;

import com.ticket.core.domain.order.command.release.HoldReleaseOutboxExecutor;
import com.ticket.core.domain.order.command.release.HoldReleaseRequestedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Clock;
import java.time.LocalDateTime;

import static com.ticket.core.infra.order.BookingBackgroundTaskConfig.BOOKING_BACKGROUND_TASK_EXECUTOR;

@Slf4j
@Component
public class HoldReleaseAfterCommitListener {

    private final HoldReleaseOutboxExecutor holdReleaseOutboxExecutor;
    private final Clock clock;
    private final TaskExecutor taskExecutor;

    public HoldReleaseAfterCommitListener(
            final HoldReleaseOutboxExecutor holdReleaseOutboxExecutor,
            final Clock clock,
            @Qualifier(BOOKING_BACKGROUND_TASK_EXECUTOR) final TaskExecutor taskExecutor
    ) {
        this.holdReleaseOutboxExecutor = holdReleaseOutboxExecutor;
        this.clock = clock;
        this.taskExecutor = taskExecutor;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleAfterCommit(final HoldReleaseRequestedEvent event) {
        try {
            taskExecutor.execute(() ->
                    holdReleaseOutboxExecutor.process(event.outboxId(), LocalDateTime.now(clock))
            );
        } catch (final TaskRejectedException e) {
            log.warn("hold release 즉시 처리 큐가 가득 찼습니다. 스케줄러가 재처리합니다. outboxId={}", event.outboxId());
        }
    }
}
