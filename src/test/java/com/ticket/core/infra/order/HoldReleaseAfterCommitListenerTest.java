package com.ticket.core.infra.order;

import com.ticket.core.infra.order.outbox.release.HoldReleaseOutboxExecutor;
import com.ticket.core.app.event.HoldReleaseRequestedEvent;
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
@SuppressWarnings("NonAsciiCharacters")
class HoldReleaseAfterCommitListenerTest {

    @Mock
    private HoldReleaseOutboxExecutor holdReleaseOutboxExecutor;

    @Test
    void after_commit_only_queues_the_outbox_task() {
        final Clock clock = Clock.fixed(Instant.parse("2026-03-25T03:00:00Z"), ZoneId.of("Asia/Seoul"));
        final AtomicReference<Runnable> queuedTask = new AtomicReference<>();
        final HoldReleaseAfterCommitListener listener = new HoldReleaseAfterCommitListener(
                holdReleaseOutboxExecutor,
                clock,
                queuedTask::set
        );

        listener.handleAfterCommit(new HoldReleaseRequestedEvent(99L));

        verifyNoInteractions(holdReleaseOutboxExecutor);

        queuedTask.get().run();
        verify(holdReleaseOutboxExecutor)
                .process(99L, LocalDateTime.of(2026, 3, 25, 12, 0));
    }

    @Test
    void 큐가_가득_차면_요청을_실패시키지_않고_스케줄러에_맡긴다() {
        final Clock clock = Clock.fixed(Instant.parse("2026-03-25T03:00:00Z"), ZoneId.of("Asia/Seoul"));
        final HoldReleaseAfterCommitListener listener = new HoldReleaseAfterCommitListener(
                holdReleaseOutboxExecutor,
                clock,
                task -> {
                    throw new TaskRejectedException("queue full");
                }
        );

        listener.handleAfterCommit(new HoldReleaseRequestedEvent(99L));

        verifyNoInteractions(holdReleaseOutboxExecutor);
    }

    @Test
    void 비동기_실행_예외는_outbox_처리를_호출한_task_안에서_격리한다() {
        final Clock clock = Clock.fixed(Instant.parse("2026-03-25T03:00:00Z"), ZoneId.of("Asia/Seoul"));
        final AtomicReference<Runnable> queuedTask = new AtomicReference<>();
        final HoldReleaseAfterCommitListener listener = new HoldReleaseAfterCommitListener(
                holdReleaseOutboxExecutor,
                clock,
                queuedTask::set
        );
        doThrow(new RuntimeException("load failed"))
                .when(holdReleaseOutboxExecutor).process(99L, LocalDateTime.of(2026, 3, 25, 12, 0));
        listener.handleAfterCommit(new HoldReleaseRequestedEvent(99L));

        assertThatCode(() -> queuedTask.get().run()).doesNotThrowAnyException();

        verify(holdReleaseOutboxExecutor).process(99L, LocalDateTime.of(2026, 3, 25, 12, 0));
    }
}
