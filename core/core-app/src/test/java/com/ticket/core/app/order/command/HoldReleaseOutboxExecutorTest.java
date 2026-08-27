package com.ticket.core.app.order.command;

import com.ticket.core.domain.order.command.release.HoldReleaseTask;
import com.ticket.core.app.lock.LockKey;
import com.ticket.core.app.lock.RecordingLockManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HoldReleaseOutboxExecutorTest {

    private static final LocalDateTime FIXED_NOW = LocalDateTime.of(2026, 3, 25, 12, 0);

    @Mock
    private HoldReleaseOutboxTransactionService transactionService;

    @Mock
    private HoldReleaseTaskProcessor taskProcessor;

    @Test
    void completesOutboxAfterReleaseTaskSucceeds() {
        final HoldReleaseTask task = new HoldReleaseTask(1L, "hold-key", List.of(10L, 20L), false);
        when(transactionService.load(1L)).thenReturn(task);

        executor().process(1L, FIXED_NOW);

        verify(taskProcessor).process(1L, task, FIXED_NOW);
        verify(transactionService).markCompleted(1L, FIXED_NOW);
    }

    @Test
    void schedulesRetryAfterReleaseTaskFails() {
        final HoldReleaseTask task = new HoldReleaseTask(1L, "hold-key", List.of(10L, 20L), false);
        when(transactionService.load(1L)).thenReturn(task);
        doThrow(new RuntimeException("release failed")).when(taskProcessor).process(1L, task, FIXED_NOW);

        executor().process(1L, FIXED_NOW);

        verify(transactionService).scheduleRetry(1L, FIXED_NOW.plusSeconds(30), "release failed");
    }

    @Test
    void doesNothingWhenTaskDoesNotExist() {
        when(transactionService.load(1L)).thenReturn(null);

        executor().process(1L, FIXED_NOW.plusSeconds(1));

        verify(transactionService).load(1L);
        verifyNoInteractions(taskProcessor);
    }

    @Test
    void entryLockContentionIsNotLoggedAsWarning() {
        executor().process(1L, LocalDateTime.of(2026, 3, 15, 12, 0));

        assertThat(lockManager.lastAcquisition().keys())
                .containsExactly(LockKey.holdReleaseOutboxEntry(1L));
        assertThat(lockManager.lastAcquisition().options().warnOnFailure()).isFalse();
    }

    private final RecordingLockManager lockManager = new RecordingLockManager();

    private HoldReleaseOutboxExecutor executor() {
        return new HoldReleaseOutboxExecutor(lockManager, transactionService, taskProcessor);
    }
}
