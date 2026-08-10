package com.ticket.core.infra.order;

import com.ticket.core.domain.hold.command.HoldCreationPostCommitNotifier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;

import static com.ticket.core.infra.order.BookingBackgroundTaskConfig.BOOKING_BACKGROUND_TASK_EXECUTOR;

@Slf4j
@Component
public class AsyncHoldCreationPostCommitNotifier implements HoldCreationPostCommitNotifier {

    private final HoldCreationOutboxExecutor outboxExecutor;
    private final TaskExecutor taskExecutor;
    private final Clock clock;

    public AsyncHoldCreationPostCommitNotifier(
            final HoldCreationOutboxExecutor outboxExecutor,
            @Qualifier(BOOKING_BACKGROUND_TASK_EXECUTOR) final TaskExecutor taskExecutor,
            final Clock clock
    ) {
        this.outboxExecutor = outboxExecutor;
        this.taskExecutor = taskExecutor;
        this.clock = clock;
    }

    @Override
    public void notify(final Long outboxId) {
        try {
            taskExecutor.execute(() -> process(outboxId));
        } catch (final TaskRejectedException e) {
            log.warn("hold creation queue is full; durable outbox will retry. outboxId={}", outboxId);
        }
    }

    private void process(final Long outboxId) {
        try {
            outboxExecutor.process(outboxId, LocalDateTime.now(clock));
        } catch (final RuntimeException e) {
            log.error("hold creation outbox could not start. outboxId={}", outboxId, e);
        }
    }
}
