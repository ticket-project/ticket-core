package com.ticket.core.infra.order;

import com.ticket.core.domain.hold.model.Hold;
import com.ticket.core.app.order.command.HoldCreationOutboxTransactionService;
import com.ticket.core.app.lock.LockKey;
import com.ticket.core.app.lock.RecordingLockManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HoldCreationOutboxExecutorTest {

    private static final LocalDateTime FIXED_NOW = LocalDateTime.of(2026, 3, 15, 12, 0);

    @Spy
    private RecordingLockManager lockManager = new RecordingLockManager();

    @Mock
    private HoldCreationOutboxTransactionService transactionService;

    @Mock
    private HoldCreationPostCommitProcessor processor;

    @InjectMocks
    private HoldCreationOutboxExecutor executor;

    @Test
    void completesOutboxAfterPostCommitProcessingSucceeds() {
        final Hold hold = hold();
        when(transactionService.load(99L)).thenReturn(hold);

        executor.process(99L, FIXED_NOW);

        verify(processor).process(hold);
        verify(transactionService).markCompleted(99L, FIXED_NOW);
    }

    @Test
    void schedulesDurableRetryAfterPostCommitProcessingFails() {
        final Hold hold = hold();
        when(transactionService.load(99L)).thenReturn(hold);
        doThrow(new RuntimeException("publish failed")).when(processor).process(hold);

        executor.process(99L, FIXED_NOW);

        verify(transactionService).scheduleRetry(99L, FIXED_NOW.plusSeconds(30), "publish failed");
    }

    @Test
    void missingOrCompletedOutboxDoesNothing() {
        when(transactionService.load(99L)).thenReturn(null);

        executor.process(99L, FIXED_NOW);

        verifyNoInteractions(processor);
    }

    @Test
    void entryLockContentionIsExpected() {
        when(transactionService.load(99L)).thenReturn(null);

        executor.process(99L, FIXED_NOW);

        assertThat(lockManager.lastAcquisition().keys())
                .containsExactly(LockKey.holdCreationOutboxEntry(99L));
        assertThat(lockManager.lastAcquisition().options().warnOnFailure()).isFalse();
    }

    private Hold hold() {
        return new Hold("hold-key", 20L, 10L, List.of(100L, 200L), FIXED_NOW);
    }
}
