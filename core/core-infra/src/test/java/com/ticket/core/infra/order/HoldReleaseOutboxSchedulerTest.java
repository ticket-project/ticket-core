package com.ticket.core.infra.order;

import com.ticket.core.domain.order.command.release.HoldReleaseOutbox;
import com.ticket.core.domain.order.command.release.HoldReleaseOutboxExecutor;
import com.ticket.core.domain.order.command.release.HoldReleaseOutboxRepository;
import com.ticket.core.domain.order.command.release.HoldReleaseOutboxStatus;
import com.ticket.support.error.CoreException;
import com.ticket.support.error.ErrorType;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.stream.LongStream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
class HoldReleaseOutboxSchedulerTest {

    @Mock
    private HoldReleaseOutboxRepository holdReleaseOutboxRepository;

    @Mock
    private HoldReleaseOutboxExecutor holdReleaseOutboxExecutor;

    @Test
    void 처리할_outbox가_없으면_종료한다() {
        when(holdReleaseOutboxRepository.findAllByStatusInAndNextAttemptAtLessThanEqual(any(), any(LocalDateTime.class), any()))
                .thenReturn(new SliceImpl<>(List.of()));

        scheduler().processPendingHoldReleases();

        verify(holdReleaseOutboxExecutor, times(0)).process(any(), any(LocalDateTime.class));
        verify(holdReleaseOutboxRepository).findAllByStatusInAndNextAttemptAtLessThanEqual(
                argThat(statuses -> statuses.containsAll(Arrays.asList(
                        HoldReleaseOutboxStatus.PENDING,
                        HoldReleaseOutboxStatus.FAILED
                )) && statuses.size() == 2),
                any(LocalDateTime.class),
                any()
        );
    }

    @Test
    void 처리할_outbox가_있으면_순서대로_처리한다() {
        final HoldReleaseOutbox first = HoldReleaseOutbox.create(1L, "hold-1", List.of(10L), LocalDateTime.of(2026, 3, 25, 12, 0));
        final HoldReleaseOutbox second = HoldReleaseOutbox.create(1L, "hold-2", List.of(20L), LocalDateTime.of(2026, 3, 25, 12, 0));
        ReflectionTestUtils.setField(first, "id", 1L);
        ReflectionTestUtils.setField(second, "id", 2L);
        final Slice<HoldReleaseOutbox> slice = new SliceImpl<>(List.of(first, second));
        when(holdReleaseOutboxRepository.findAllByStatusInAndNextAttemptAtLessThanEqual(any(), any(LocalDateTime.class), any()))
                .thenReturn(slice);

        scheduler().processPendingHoldReleases();

        verify(holdReleaseOutboxExecutor).process(eq(1L), any(LocalDateTime.class));
        verify(holdReleaseOutboxExecutor).process(eq(2L), any(LocalDateTime.class));
    }

    @Test
    void 한_outbox가_실행중이어도_다음_outbox를_계속_처리한다() {
        final HoldReleaseOutbox first = HoldReleaseOutbox.create(1L, "hold-1", List.of(10L), LocalDateTime.of(2026, 3, 25, 12, 0));
        final HoldReleaseOutbox second = HoldReleaseOutbox.create(1L, "hold-2", List.of(20L), LocalDateTime.of(2026, 3, 25, 12, 0));
        ReflectionTestUtils.setField(first, "id", 1L);
        ReflectionTestUtils.setField(second, "id", 2L);
        when(holdReleaseOutboxRepository.findAllByStatusInAndNextAttemptAtLessThanEqual(any(), any(LocalDateTime.class), any()))
                .thenReturn(new SliceImpl<>(List.of(first, second)));
        doThrow(new RuntimeException("already processing"))
                .when(holdReleaseOutboxExecutor).process(eq(1L), any(LocalDateTime.class));

        scheduler().processPendingHoldReleases();

        verify(holdReleaseOutboxExecutor).process(eq(1L), any(LocalDateTime.class));
        verify(holdReleaseOutboxExecutor).process(eq(2L), any(LocalDateTime.class));
    }

    @Test
    void 가득_찬_페이지에서_실행_시작이_실패하면_같은_페이지를_다시_조회하지_않는다() {
        final List<HoldReleaseOutbox> outboxes = LongStream.rangeClosed(1L, 100L)
                .mapToObj(this::outbox)
                .toList();
        final Slice<HoldReleaseOutbox> fullSlice = new SliceImpl<>(outboxes);
        when(holdReleaseOutboxRepository.findAllByStatusInAndNextAttemptAtLessThanEqual(any(), any(LocalDateTime.class), any()))
                .thenReturn(fullSlice)
                .thenThrow(new AssertionError("같은 due 페이지를 즉시 다시 조회하면 안 됩니다."));
        doThrow(new CoreException(ErrorType.HOLD_BUSY))
                .when(holdReleaseOutboxExecutor).process(eq(1L), any(LocalDateTime.class));

        scheduler().processPendingHoldReleases();

        verify(holdReleaseOutboxRepository, times(1))
                .findAllByStatusInAndNextAttemptAtLessThanEqual(any(), any(LocalDateTime.class), any());
        verify(holdReleaseOutboxExecutor).process(eq(1L), any(LocalDateTime.class));
        verify(holdReleaseOutboxExecutor).process(eq(100L), any(LocalDateTime.class));
    }

    private HoldReleaseOutbox outbox(final long id) {
        final HoldReleaseOutbox outbox = HoldReleaseOutbox.create(
                1L,
                "hold-" + id,
                List.of(id),
                LocalDateTime.of(2026, 3, 25, 12, 0)
        );
        ReflectionTestUtils.setField(outbox, "id", id);
        return outbox;
    }

    private HoldReleaseOutboxScheduler scheduler() {
        final Clock clock = Clock.fixed(Instant.parse("2026-03-25T03:00:00Z"), ZoneId.of("Asia/Seoul"));
        return new HoldReleaseOutboxScheduler(holdReleaseOutboxRepository, holdReleaseOutboxExecutor, clock);
    }
}
