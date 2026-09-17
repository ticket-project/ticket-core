package com.ticket.booking.selection.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.ticket.booking.application.RecordingLockManager;
import com.ticket.booking.application.SeatStatusEvent.SeatStatusAction;
import com.ticket.booking.application.concurrency.LockKey;
import com.ticket.booking.application.port.SeatStatusEventPublisher;
import com.ticket.booking.domain.seat.PerformanceSeat;
import com.ticket.booking.domain.seat.PerformanceSeatRepository;
import com.ticket.booking.domain.seat.PerformanceSeatState;
import com.ticket.booking.exception.PerformanceIsPastException;
import com.ticket.booking.exception.SeatAlreadyHeldException;
import com.ticket.booking.hold.domain.HoldManager;
import com.ticket.booking.selection.domain.SeatSelectionService;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
class SeatSelectionCoordinatorTest {
    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-08-04T01:00:00Z"), ZoneId.of("Asia/Seoul"));
    private static final LocalDateTime NOW = LocalDateTime.now(CLOCK);
    @Mock private HoldManager holdManager;
    @Mock private SeatSelectionService seatSelectionService;
    @Mock private PerformanceSeatRepository performanceSeatRepository;
    @Mock private SeatStatusEventPublisher seatEventPublisher;
    private final RecordingLockManager lockManager = new RecordingLockManager();
    private SeatSelectionCoordinator coordinator;

    @BeforeEach
    void setUp() {
        coordinator =
                new SeatSelectionCoordinator(
                        lockManager,
                        holdManager,
                        seatSelectionService,
                        performanceSeatRepository,
                        seatEventPublisher,
                        CLOCK);
    }

    @Test
    void 락_내부에서_홀드를_다시_확인하고_좌석을_선점한다() {
        when(holdManager.isHeld(10L, 20L)).thenReturn(false);

        coordinator.select(10L, 20L, 1L, 501L, NOW.plusMinutes(1));

        verify(seatSelectionService).select(10L, 20L, 1L);
    }

    /** 발행이 락 밖에 있으면 뒤늦은 만료 알림이 이 SELECTED 뒤에 끼어들 수 있다. */
    @Test
    void SELECTED_발행은_좌석_락_안에서_한다() {
        when(holdManager.isHeld(10L, 20L)).thenReturn(false);

        coordinator.select(10L, 20L, 1L, 501L, NOW.plusMinutes(1));

        verify(seatEventPublisher).publish(10L, 501L, 20L, SeatStatusAction.SELECTED);
        assertThat(lockManager.allKeys()).containsExactly(LockKey.seat(10L, 20L));
    }

    @Test
    void DB검증_후_홀드된_좌석이면_선점을_중단한다() {
        when(holdManager.isHeld(10L, 20L)).thenReturn(true);

        assertThatThrownBy(() -> coordinator.select(10L, 20L, 1L, 501L, NOW.plusMinutes(1)))
                .isInstanceOf(SeatAlreadyHeldException.class)
                .hasFieldOrPropertyWithValue("performanceId", 10L)
                .hasFieldOrPropertyWithValue("seatId", 20L);

        verifyNoInteractions(seatSelectionService, seatEventPublisher);
    }

    @Test
    void 락_획득_시점에_예매가_마감됐으면_선점을_중단한다() {
        assertThatThrownBy(() -> coordinator.select(10L, 20L, 1L, 501L, NOW.minusNanos(1)))
                .isInstanceOf(PerformanceIsPastException.class)
                .hasFieldOrPropertyWithValue("performanceId", 10L);

        verifyNoInteractions(holdManager, seatSelectionService, seatEventPublisher);
    }

    @Test
    void 해제되면_performanceSeatId와_함께_DESELECTED를_발행한다() {
        givenPerformanceSeat();
        when(seatSelectionService.deselect(10L, 20L, 1L)).thenReturn(true);

        coordinator.deselect(10L, 20L, 1L);

        verify(seatEventPublisher).publish(10L, 501L, 20L, SeatStatusAction.DESELECTED);
    }

    /** 이미 만료된 선택을 해제 요청해도 아무 일도 일어나지 않았으므로 알리지 않는다. */
    @Test
    void 실제로_해제되지_않았으면_발행하지_않는다() {
        givenPerformanceSeat();
        when(seatSelectionService.deselect(10L, 20L, 1L)).thenReturn(false);

        coordinator.deselect(10L, 20L, 1L);

        verifyNoInteractions(seatEventPublisher);
    }

    /**
     * 결함 재현: A의 선택이 만료되고 B가 같은 좌석을 다시 선택한 뒤 A의 만료 처리가 뒤늦게 실행되는 경우다. 그대로 발행하면 B가 잡고 있는 좌석이 비어 보인다.
     */
    @Test
    void 만료_알림_직전에_다른_사용자가_다시_선택했으면_발행하지_않는다() {
        givenPerformanceSeat();
        when(seatSelectionService.isSelected(10L, 20L)).thenReturn(true);

        coordinator.notifyReleasedIfFree(10L, 20L);

        verify(seatEventPublisher, never()).publish(anyLong(), anyLong(), anyLong(), any());
        assertThat(lockManager.allKeys()).containsExactly(LockKey.seat(10L, 20L));
    }

    @Test
    void 만료_알림_직전에_선점으로_넘어갔으면_발행하지_않는다() {
        givenPerformanceSeat();
        when(seatSelectionService.isSelected(10L, 20L)).thenReturn(false);
        when(holdManager.isHeld(10L, 20L)).thenReturn(true);

        coordinator.notifyReleasedIfFree(10L, 20L);

        verify(seatEventPublisher, never()).publish(anyLong(), anyLong(), anyLong(), any());
    }

    @Test
    void 만료된_좌석이_여전히_비어_있으면_DESELECTED를_발행한다() {
        givenPerformanceSeat();
        when(seatSelectionService.isSelected(10L, 20L)).thenReturn(false);
        when(holdManager.isHeld(10L, 20L)).thenReturn(false);

        coordinator.notifyReleasedIfFree(10L, 20L);

        verify(seatEventPublisher).publish(10L, 501L, 20L, SeatStatusAction.DESELECTED);
    }

    /** 여러 좌석을 한꺼번에 알릴 때 좌석마다 DB를 다시 읽지 않도록 미리 조회한 id를 그대로 받는다. */
    @Test
    void performanceSeatId를_받으면_좌석을_다시_조회하지_않는다() {
        when(seatSelectionService.isSelected(10L, 20L)).thenReturn(false);
        when(holdManager.isHeld(10L, 20L)).thenReturn(false);

        coordinator.notifyReleasedIfFree(10L, 20L, 501L);

        verifyNoInteractions(performanceSeatRepository);
        verify(seatEventPublisher).publish(10L, 501L, 20L, SeatStatusAction.DESELECTED);
    }

    private void givenPerformanceSeat() {
        final PerformanceSeat performanceSeat =
                new PerformanceSeat(10L, 20L, 30L, PerformanceSeatState.AVAILABLE, BigDecimal.TEN);
        ReflectionTestUtils.setField(performanceSeat, "id", 501L);
        when(performanceSeatRepository.findAllByPerformanceIdAndSeatIdIn(10L, List.of(20L)))
                .thenReturn(List.of(performanceSeat));
    }
}
