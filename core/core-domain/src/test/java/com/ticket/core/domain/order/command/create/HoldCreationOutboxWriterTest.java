package com.ticket.core.domain.order.command.create;

import com.ticket.core.domain.hold.model.HoldSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HoldCreationOutboxWriterTest {

    @Mock
    private HoldCreationOutboxRepository repository;

    @Test
    void appendsEverythingNeededToRetryPostCommitProcessing() {
        final HoldSnapshot snapshot = snapshot();
        final LocalDateTime nextAttemptAt = LocalDateTime.of(2026, 3, 15, 11, 50);
        final HoldCreationOutbox saved = HoldCreationOutbox.create(snapshot, nextAttemptAt);
        ReflectionTestUtils.setField(saved, "id", 99L);
        when(repository.save(argThat(outbox ->
                outbox.snapshot().equals(snapshot)
                        && outbox.getNextAttemptAt().equals(nextAttemptAt)
                        && outbox.getStatus() == HoldCreationOutboxStatus.PENDING
        ))).thenReturn(saved);

        final Long outboxId = new HoldCreationOutboxWriter(repository).append(snapshot, nextAttemptAt);

        assertThat(outboxId).isEqualTo(99L);
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
