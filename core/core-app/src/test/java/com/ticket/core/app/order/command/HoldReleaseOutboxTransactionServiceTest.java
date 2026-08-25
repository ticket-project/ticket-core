package com.ticket.core.app.order.command;

import com.ticket.core.domain.order.command.release.HoldReleaseTask;
import com.ticket.core.domain.order.command.release.HoldReleaseOutboxStatus;
import com.ticket.core.domain.order.command.release.HoldReleaseOutboxRepository;
import com.ticket.core.domain.order.command.release.HoldReleaseOutbox;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HoldReleaseOutboxTransactionServiceTest {

    private static final LocalDateTime FIXED_NOW = LocalDateTime.of(2026, 3, 25, 12, 0);

    @Mock
    private HoldReleaseOutboxRepository holdReleaseOutboxRepository;

    @Test
    void load_returns_an_immutable_task_for_a_pending_outbox() {
        final HoldReleaseOutbox outbox = outbox();
        when(holdReleaseOutboxRepository.findById(1L)).thenReturn(Optional.of(outbox));

        final HoldReleaseTask task = service().load(1L);

        assertThat(task).isEqualTo(new HoldReleaseTask(10L, "hold-key", List.of(100L, 200L), false));
    }

    @Test
    void load_returns_null_for_a_completed_outbox() {
        final HoldReleaseOutbox outbox = outbox();
        outbox.markCompleted(FIXED_NOW);
        when(holdReleaseOutboxRepository.findById(1L)).thenReturn(Optional.of(outbox));

        assertThat(service().load(1L)).isNull();
    }

    @Test
    void mark_completed_changes_state_in_a_short_transaction() {
        final HoldReleaseOutbox outbox = outbox();
        when(holdReleaseOutboxRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(outbox));

        service().markCompleted(1L, FIXED_NOW);

        assertThat(outbox.isCompleted()).isTrue();
        assertThat(outbox.getCompletedAt()).isEqualTo(FIXED_NOW);
    }

    @Test
    void mark_hold_released_persists_the_external_step_before_publication() {
        final HoldReleaseOutbox outbox = outbox();
        when(holdReleaseOutboxRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(outbox));

        service().markHoldReleased(1L, FIXED_NOW);

        assertThat(outbox.isHoldReleased()).isTrue();
        assertThat(outbox.getHoldReleasedAt()).isEqualTo(FIXED_NOW);
    }

    @Test
    void schedule_retry_records_the_next_attempt() {
        final HoldReleaseOutbox outbox = outbox();
        when(holdReleaseOutboxRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(outbox));

        service().scheduleRetry(1L, FIXED_NOW.plusSeconds(30), "release failed");

        assertThat(outbox.getStatus()).isEqualTo(HoldReleaseOutboxStatus.FAILED);
        assertThat(outbox.getRetryCount()).isEqualTo(1);
        assertThat(outbox.getNextAttemptAt()).isEqualTo(FIXED_NOW.plusSeconds(30));
        assertThat(outbox.getLastError()).isEqualTo("release failed");
    }

    @Test
    void executor_itself_is_not_transactional() throws NoSuchMethodException {
        assertThat(HoldReleaseOutboxExecutor.class
                .getDeclaredMethod("process", Long.class, LocalDateTime.class)
                .isAnnotationPresent(Transactional.class))
                .isFalse();
        assertThat(HoldReleaseOutboxTransactionService.class
                .getDeclaredMethod("load", Long.class)
                .isAnnotationPresent(Transactional.class))
                .isTrue();
        assertThat(HoldReleaseOutboxTransactionService.class
                .getDeclaredMethod("markCompleted", Long.class, LocalDateTime.class)
                .isAnnotationPresent(Transactional.class))
                .isTrue();
        assertThat(HoldReleaseOutboxTransactionService.class
                .getDeclaredMethod("markHoldReleased", Long.class, LocalDateTime.class)
                .isAnnotationPresent(Transactional.class))
                .isTrue();
    }

    @Test
    void missing_or_completed_outbox_is_not_changed_again() {
        final HoldReleaseOutbox completed = outbox();
        completed.markCompleted(FIXED_NOW);
        when(holdReleaseOutboxRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.empty(), Optional.of(completed));

        service().markCompleted(1L, FIXED_NOW.plusSeconds(1));
        service().scheduleRetry(1L, FIXED_NOW.plusSeconds(30), "late failure");

        verify(holdReleaseOutboxRepository, org.mockito.Mockito.times(2)).findByIdForUpdate(1L);
        assertThat(completed.getCompletedAt()).isEqualTo(FIXED_NOW);
        assertThat(completed.getStatus()).isEqualTo(HoldReleaseOutboxStatus.COMPLETED);
    }

    private HoldReleaseOutboxTransactionService service() {
        return new HoldReleaseOutboxTransactionService(holdReleaseOutboxRepository);
    }

    private HoldReleaseOutbox outbox() {
        final HoldReleaseOutbox outbox = HoldReleaseOutbox.create(
                10L,
                "hold-key",
                List.of(100L, 200L),
                FIXED_NOW
        );
        ReflectionTestUtils.setField(outbox, "id", 1L);
        return outbox;
    }
}
