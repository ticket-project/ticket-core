package com.ticket.core.infra.order;

import com.ticket.core.domain.hold.model.HoldSnapshot;
import com.ticket.core.domain.order.command.create.HoldCreationOutboxTransactionService;
import com.ticket.core.support.lock.DistributedLock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
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
class HoldCreationOutboxExecutorTest {

    private static final LocalDateTime FIXED_NOW = LocalDateTime.of(2026, 3, 15, 12, 0);

    @Mock
    private HoldCreationOutboxTransactionService transactionService;

    @Mock
    private HoldCreationPostCommitProcessor processor;

    @InjectMocks
    private HoldCreationOutboxExecutor executor;

    @Test
    void completesOutboxAfterPostCommitProcessingSucceeds() {
        final HoldSnapshot snapshot = snapshot();
        when(transactionService.load(99L)).thenReturn(snapshot);

        executor.process(99L, FIXED_NOW);

        verify(processor).process(snapshot);
        verify(transactionService).markCompleted(99L, FIXED_NOW);
    }

    @Test
    void schedulesDurableRetryAfterPostCommitProcessingFails() {
        final HoldSnapshot snapshot = snapshot();
        when(transactionService.load(99L)).thenReturn(snapshot);
        doThrow(new RuntimeException("publish failed")).when(processor).process(snapshot);

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
    void entryLockContentionIsExpected() throws NoSuchMethodException {
        final DistributedLock lock = HoldCreationOutboxExecutor.class
                .getMethod("process", Long.class, LocalDateTime.class)
                .getAnnotation(DistributedLock.class);

        assertThat(lock.warnOnFailure()).isFalse();
    }

    private HoldSnapshot snapshot() {
        return new HoldSnapshot("hold-key", 20L, 10L, List.of(100L, 200L), FIXED_NOW);
    }
}
