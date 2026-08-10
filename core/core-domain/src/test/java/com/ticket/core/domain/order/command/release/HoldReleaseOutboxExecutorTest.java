package com.ticket.core.domain.order.command.release;

import com.ticket.core.support.lock.DistributedLock;
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
        final HoldReleaseTask task = new HoldReleaseTask(1L, "hold-key", List.of(10L, 20L));
        when(transactionService.load(1L)).thenReturn(task);

        executor().process(1L, FIXED_NOW);

        verify(taskProcessor).process(task);
        verify(transactionService).markCompleted(1L, FIXED_NOW);
    }

    @Test
    void schedulesRetryAfterReleaseTaskFails() {
        final HoldReleaseTask task = new HoldReleaseTask(1L, "hold-key", List.of(10L, 20L));
        when(transactionService.load(1L)).thenReturn(task);
        doThrow(new RuntimeException("release failed")).when(taskProcessor).process(task);

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
    void entryLockContentionIsNotLoggedAsWarning() throws NoSuchMethodException {
        final DistributedLock lock = HoldReleaseOutboxExecutor.class
                .getMethod("process", Long.class, LocalDateTime.class)
                .getAnnotation(DistributedLock.class);

        assertThat(lock.warnOnFailure()).isFalse();
    }

    private HoldReleaseOutboxExecutor executor() {
        return new HoldReleaseOutboxExecutor(transactionService, taskProcessor);
    }
}
