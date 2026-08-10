package com.ticket.core.domain.order.command.create;

import com.ticket.core.domain.hold.model.HoldSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HoldCreationOutboxTransactionServiceTest {

    private static final LocalDateTime FIXED_NOW = LocalDateTime.of(2026, 3, 15, 12, 0);

    @Mock
    private HoldCreationOutboxRepository repository;

    @Test
    void loadsSnapshotFromPendingOutbox() {
        final HoldCreationOutbox outbox = outbox();
        when(repository.findById(1L)).thenReturn(Optional.of(outbox));

        assertThat(service().load(1L)).isEqualTo(snapshot());
    }

    @Test
    void completedOutboxIsNotLoadedAgain() {
        final HoldCreationOutbox outbox = outbox();
        outbox.markCompleted(FIXED_NOW);
        when(repository.findById(1L)).thenReturn(Optional.of(outbox));

        assertThat(service().load(1L)).isNull();
    }

    @Test
    void recordsCompletionAndRetryInShortTransactions() {
        final HoldCreationOutbox retryOutbox = outbox();
        final HoldCreationOutbox completedOutbox = outbox();
        when(repository.findByIdForUpdate(1L)).thenReturn(Optional.of(retryOutbox));
        when(repository.findByIdForUpdate(2L)).thenReturn(Optional.of(completedOutbox));

        service().scheduleRetry(1L, FIXED_NOW.plusSeconds(30), "publish failed");
        service().markCompleted(2L, FIXED_NOW);

        assertThat(retryOutbox.getStatus()).isEqualTo(HoldCreationOutboxStatus.FAILED);
        assertThat(retryOutbox.getRetryCount()).isEqualTo(1);
        assertThat(retryOutbox.getNextAttemptAt()).isEqualTo(FIXED_NOW.plusSeconds(30));
        assertThat(completedOutbox.isCompleted()).isTrue();
    }

    @Test
    void stateChangingMethodsAreTransactional() throws NoSuchMethodException {
        assertThat(HoldCreationOutboxTransactionService.class
                .getDeclaredMethod("markCompleted", Long.class, LocalDateTime.class)
                .isAnnotationPresent(Transactional.class)).isTrue();
        assertThat(HoldCreationOutboxTransactionService.class
                .getDeclaredMethod("scheduleRetry", Long.class, LocalDateTime.class, String.class)
                .isAnnotationPresent(Transactional.class)).isTrue();
    }

    private HoldCreationOutboxTransactionService service() {
        return new HoldCreationOutboxTransactionService(repository);
    }

    private HoldCreationOutbox outbox() {
        return HoldCreationOutbox.create(snapshot(), FIXED_NOW.minusMinutes(10));
    }

    private HoldSnapshot snapshot() {
        return new HoldSnapshot("hold-key", 20L, 10L, List.of(100L, 200L), FIXED_NOW);
    }
}
