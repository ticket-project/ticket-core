package com.ticket.core.infra.order;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskRejectedException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class AsyncHoldCreationPostCommitNotifierTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-03-15T03:00:00Z"),
            ZoneId.of("Asia/Seoul")
    );
    private static final LocalDateTime FIXED_NOW = LocalDateTime.of(2026, 3, 15, 12, 0);

    @Mock
    private HoldCreationOutboxExecutor outboxExecutor;

    @Test
    void notificationOnlyQueuesTheDurableOutboxId() {
        final AtomicReference<Runnable> queuedTask = new AtomicReference<>();
        final AsyncHoldCreationPostCommitNotifier notifier = notifier(queuedTask::set);

        notifier.notify(99L);

        verifyNoInteractions(outboxExecutor);
        queuedTask.get().run();
        verify(outboxExecutor).process(99L, FIXED_NOW);
    }

    @Test
    void aFullQueueDoesNotFailTheCommittedOrder() {
        final AsyncHoldCreationPostCommitNotifier notifier = notifier(task -> {
            throw new TaskRejectedException("queue full");
        });

        assertThatCode(() -> notifier.notify(99L)).doesNotThrowAnyException();

        verifyNoInteractions(outboxExecutor);
    }

    @Test
    void executorStartupFailureIsContainedInsideTheBackgroundTask() {
        final AtomicReference<Runnable> queuedTask = new AtomicReference<>();
        final AsyncHoldCreationPostCommitNotifier notifier = notifier(queuedTask::set);
        doThrow(new RuntimeException("database failed"))
                .when(outboxExecutor).process(99L, FIXED_NOW);
        notifier.notify(99L);

        assertThatCode(() -> queuedTask.get().run()).doesNotThrowAnyException();

        verify(outboxExecutor).process(99L, FIXED_NOW);
    }

    private AsyncHoldCreationPostCommitNotifier notifier(final org.springframework.core.task.TaskExecutor executor) {
        return new AsyncHoldCreationPostCommitNotifier(outboxExecutor, executor, FIXED_CLOCK);
    }
}
