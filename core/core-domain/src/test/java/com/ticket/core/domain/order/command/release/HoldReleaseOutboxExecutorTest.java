package com.ticket.core.domain.order.command.release;

import com.ticket.core.domain.hold.command.HoldManager;
import com.ticket.core.domain.performanceseat.command.SeatStatusPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
class HoldReleaseOutboxExecutorTest {

    @Mock
    private HoldReleaseOutboxRepository holdReleaseOutboxRepository;

    @Mock
    private HoldManager holdManager;

    @Mock
    private SeatStatusPublisher seatStatusPublisher;

    private static final LocalDateTime FIXED_NOW = LocalDateTime.of(2026, 3, 25, 12, 0);

    @Test
    void hold_release가_성공하면_좌석_해제_이벤트를_발행하고_outbox를_완료처리한다() {
        final HoldReleaseOutbox outbox = HoldReleaseOutbox.create(1L, "hold-key", List.of(10L, 20L), FIXED_NOW);
        when(holdReleaseOutboxRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(outbox));

        processor().process(1L, FIXED_NOW);

        verify(holdReleaseOutboxRepository).findByIdForUpdate(1L);
        verify(holdManager).release(1L, "hold-key", List.of(10L, 20L));
        verify(seatStatusPublisher).publishReleased(1L, List.of(10L, 20L));
        assertThat(outbox.isCompleted()).isTrue();
        assertThat(outbox.getStatus()).isEqualTo(HoldReleaseOutboxStatus.COMPLETED);
        assertThat(outbox.getCompletedAt()).isEqualTo(FIXED_NOW);
        assertThat(outbox.getRetryCount()).isZero();
    }

    @Test
    void hold_release가_실패하면_다음_재시도_시각만_기록하고_해제_이벤트는_발행하지_않는다() {
        final HoldReleaseOutbox outbox = HoldReleaseOutbox.create(1L, "hold-key", List.of(10L, 20L), FIXED_NOW);
        when(holdReleaseOutboxRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(outbox));
        doThrow(new RuntimeException("release failed")).when(holdManager).release(1L, "hold-key", List.of(10L, 20L));

        processor().process(1L, FIXED_NOW);

        verify(holdReleaseOutboxRepository).findByIdForUpdate(1L);
        verifyNoInteractions(seatStatusPublisher);
        assertThat(outbox.isCompleted()).isFalse();
        assertThat(outbox.getStatus()).isEqualTo(HoldReleaseOutboxStatus.FAILED);
        assertThat(outbox.getRetryCount()).isEqualTo(1);
        assertThat(outbox.getNextAttemptAt()).isEqualTo(FIXED_NOW.plusSeconds(30));
        assertThat(outbox.getLastError()).contains("release failed");
    }

    @Test
    void completed_outbox_does_not_repeat_external_side_effects() {
        final HoldReleaseOutbox outbox = HoldReleaseOutbox.create(1L, "hold-key", List.of(10L, 20L), FIXED_NOW);
        outbox.markCompleted(FIXED_NOW);
        when(holdReleaseOutboxRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(outbox));

        processor().process(1L, FIXED_NOW.plusSeconds(1));

        verify(holdReleaseOutboxRepository).findByIdForUpdate(1L);
        verifyNoInteractions(holdManager, seatStatusPublisher);
        assertThat(outbox.getCompletedAt()).isEqualTo(FIXED_NOW);
    }

    @Test
    void missing_outbox_does_nothing() {
        when(holdReleaseOutboxRepository.findByIdForUpdate(1L)).thenReturn(Optional.empty());

        processor().process(1L, FIXED_NOW);

        verify(holdReleaseOutboxRepository).findByIdForUpdate(1L);
        verifyNoInteractions(holdManager, seatStatusPublisher);
    }

    private HoldReleaseOutboxExecutor processor() {
        return new HoldReleaseOutboxExecutor(holdReleaseOutboxRepository, holdManager, seatStatusPublisher);
    }
}
