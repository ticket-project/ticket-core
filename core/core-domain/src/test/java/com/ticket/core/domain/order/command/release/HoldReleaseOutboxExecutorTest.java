package com.ticket.core.domain.order.command.release;

import com.ticket.core.domain.hold.command.HoldManager;
import com.ticket.core.domain.performanceseat.command.SeatStatusPublisher;
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
@SuppressWarnings("NonAsciiCharacters")
class HoldReleaseOutboxExecutorTest {

    @Mock
    private HoldReleaseOutboxTransactionService transactionService;

    @Mock
    private HoldManager holdManager;

    @Mock
    private SeatStatusPublisher seatStatusPublisher;

    private static final LocalDateTime FIXED_NOW = LocalDateTime.of(2026, 3, 25, 12, 0);

    @Test
    void hold_release가_성공하면_좌석_해제_이벤트를_발행하고_outbox를_완료처리한다() {
        final HoldReleaseTask task = new HoldReleaseTask(1L, "hold-key", List.of(10L, 20L));
        when(transactionService.load(1L)).thenReturn(task);

        processor().process(1L, FIXED_NOW);

        verify(transactionService).load(1L);
        verify(holdManager).release(1L, "hold-key", List.of(10L, 20L));
        verify(seatStatusPublisher).publishReleased(1L, List.of(10L, 20L));
        verify(transactionService).markCompleted(1L, FIXED_NOW);
    }

    @Test
    void hold_release가_실패하면_다음_재시도_시각만_기록하고_해제_이벤트는_발행하지_않는다() {
        final HoldReleaseTask task = new HoldReleaseTask(1L, "hold-key", List.of(10L, 20L));
        when(transactionService.load(1L)).thenReturn(task);
        doThrow(new RuntimeException("release failed")).when(holdManager).release(1L, "hold-key", List.of(10L, 20L));

        processor().process(1L, FIXED_NOW);

        verify(transactionService).load(1L);
        verifyNoInteractions(seatStatusPublisher);
        verify(transactionService).scheduleRetry(1L, FIXED_NOW.plusSeconds(30), "release failed");
    }

    @Test
    void no_task_does_nothing() {
        when(transactionService.load(1L)).thenReturn(null);

        processor().process(1L, FIXED_NOW.plusSeconds(1));

        verify(transactionService).load(1L);
        verifyNoInteractions(holdManager, seatStatusPublisher);
    }

    @Test
    void entry_lock_contention_is_not_logged_as_a_warning() throws NoSuchMethodException {
        final DistributedLock lock = HoldReleaseOutboxExecutor.class
                .getMethod("process", Long.class, LocalDateTime.class)
                .getAnnotation(DistributedLock.class);

        assertThat(lock.warnOnFailure()).isFalse();
    }

    private HoldReleaseOutboxExecutor processor() {
        return new HoldReleaseOutboxExecutor(transactionService, holdManager, seatStatusPublisher);
    }
}
