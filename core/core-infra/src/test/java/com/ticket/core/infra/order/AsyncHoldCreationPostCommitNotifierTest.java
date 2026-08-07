package com.ticket.core.infra.order;

import com.ticket.core.domain.hold.model.HoldSnapshot;
import com.ticket.core.domain.performanceseat.command.SeatSelectionService;
import com.ticket.core.domain.performanceseat.command.SeatStatusPublisher;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskRejectedException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class AsyncHoldCreationPostCommitNotifierTest {

    @Mock
    private SeatSelectionService seatSelectionService;

    @Mock
    private SeatStatusPublisher seatStatusPublisher;

    @Test
    void notification_only_queues_external_side_effects() {
        final AtomicReference<Runnable> queuedTask = new AtomicReference<>();
        final HoldSnapshot snapshot = snapshot();
        final AsyncHoldCreationPostCommitNotifier notifier = new AsyncHoldCreationPostCommitNotifier(
                seatSelectionService,
                seatStatusPublisher,
                queuedTask::set
        );

        notifier.notify(snapshot);

        verifyNoInteractions(seatSelectionService, seatStatusPublisher);

        queuedTask.get().run();
        final InOrder inOrder = inOrder(seatSelectionService, seatStatusPublisher);
        inOrder.verify(seatSelectionService).forceDeselect(10L, 100L);
        inOrder.verify(seatSelectionService).forceDeselect(10L, 200L);
        inOrder.verify(seatStatusPublisher).publishHeld(10L, List.of(100L, 200L));
    }

    @Test
    void a_full_queue_does_not_fail_the_committed_order() {
        final AsyncHoldCreationPostCommitNotifier notifier = new AsyncHoldCreationPostCommitNotifier(
                seatSelectionService,
                seatStatusPublisher,
                task -> {
                    throw new TaskRejectedException("queue full");
                }
        );

        assertThatCode(() -> notifier.notify(snapshot())).doesNotThrowAnyException();

        verifyNoInteractions(seatSelectionService, seatStatusPublisher);
    }

    @Test
    void external_failure_is_contained_inside_the_background_task() {
        final AtomicReference<Runnable> queuedTask = new AtomicReference<>();
        final HoldSnapshot snapshot = snapshot();
        final AsyncHoldCreationPostCommitNotifier notifier = new AsyncHoldCreationPostCommitNotifier(
                seatSelectionService,
                seatStatusPublisher,
                queuedTask::set
        );
        doThrow(new RuntimeException("redis failed"))
                .when(seatSelectionService).forceDeselect(10L, 100L);
        notifier.notify(snapshot);

        assertThatCode(() -> queuedTask.get().run()).doesNotThrowAnyException();

        verifyNoInteractions(seatStatusPublisher);
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
