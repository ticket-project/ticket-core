package com.ticket.booking.internal.infrastructure.order;

import com.ticket.core.support.exception.CoreException;
import com.ticket.core.support.exception.ErrorType;
import com.ticket.booking.internal.infrastructure.order.outbox.release.HoldReleaseOutboxExecutor;
import com.ticket.booking.internal.application.event.HoldReleaseRequestedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Clock;
import java.time.LocalDateTime;

import static com.ticket.booking.internal.infrastructure.order.BookingBackgroundTaskConfig.BOOKING_BACKGROUND_TASK_EXECUTOR;

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
            taskExecutor.execute(() -> process(event.eventId()));
        } catch (final TaskRejectedException e) {
            log.warn("hold release 즉시 처리 큐가 가득 찼습니다. 스케줄러가 재처리합니다. outboxId={}", event.eventId());
        }
    }

    private void process(final Long outboxId) {
        try {
            holdReleaseOutboxExecutor.process(outboxId, LocalDateTime.now(clock));
        } catch (final CoreException e) {
            if (e.getErrorType() == ErrorType.HOLD_BUSY) {
                log.debug("hold release outbox가 이미 처리 중입니다. outboxId={}", outboxId);
                return;
            }
            log.error("hold release 즉시 처리에 실패했습니다. outboxId={}", outboxId, e);
        } catch (final RuntimeException e) {
            log.error("hold release 즉시 처리에 실패했습니다. outboxId={}", outboxId, e);
        }
    }
}
