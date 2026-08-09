package com.ticket.core.infra.order;

import com.ticket.core.domain.hold.model.HoldSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskRejectedException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class AsyncHoldCreationPostCommitNotifierTest {

    @Mock
    private HoldCreationPostCommitProcessor processor;

    @Test
    void notification_only_queues_external_side_effects() {
        final AtomicReference<Runnable> queuedTask = new AtomicReference<>();
        final HoldSnapshot snapshot = snapshot();
        final AsyncHoldCreationPostCommitNotifier notifier = new AsyncHoldCreationPostCommitNotifier(
                processor,
                queuedTask::set
        );

        notifier.notify(snapshot);

        verifyNoInteractions(processor);

        queuedTask.get().run();
        verify(processor).process(snapshot);
    }

    @Test
    void a_full_queue_does_not_fail_the_committed_order() {
        final AsyncHoldCreationPostCommitNotifier notifier = new AsyncHoldCreationPostCommitNotifier(
                processor,
                task -> {
                    throw new TaskRejectedException("queue full");
                }
        );

        assertThatCode(() -> notifier.notify(snapshot())).doesNotThrowAnyException();

        verifyNoInteractions(processor);
    }

    @Test
    void external_failure_is_contained_inside_the_background_task() {
        final AtomicReference<Runnable> queuedTask = new AtomicReference<>();
        final HoldSnapshot snapshot = snapshot();
        final AsyncHoldCreationPostCommitNotifier notifier = new AsyncHoldCreationPostCommitNotifier(
                processor,
                queuedTask::set
        );
        doThrow(new RuntimeException("redis failed"))
                .when(processor).process(snapshot);
        notifier.notify(snapshot);

        assertThatCode(() -> queuedTask.get().run()).doesNotThrowAnyException();

        verify(processor).process(snapshot);
    }

    private HoldSnapshot snapshot() {
        return new HoldSnapshot(
                "hold-key",
                20L,
                10L,
                List.of(100L, 200L),
                LocalDateTime.of(2026, 3, 15, 12, 0)
        );
    }
}
