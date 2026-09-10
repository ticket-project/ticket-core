package com.ticket.booking.hold.application;

import com.ticket.booking.hold.application.HoldReleaseProgressRecorder;
import com.ticket.booking.hold.domain.HoldManager;
import com.ticket.booking.selection.domain.SeatSelectionService;
import com.ticket.booking.seat.domain.PerformanceSeat;
import com.ticket.booking.seat.domain.PerformanceSeatState;
import com.ticket.booking.seat.domain.PerformanceSeatRepository;
import com.ticket.booking.seat.application.SeatStatusEvent.SeatStatusAction;
import com.ticket.booking.seat.application.SeatStatusEventPublisher;
import com.ticket.booking.application.LockKey;
import com.ticket.booking.application.RecordingLockManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HoldReleaseTaskProcessorTest {

    private static final LocalDateTime FIXED_NOW = LocalDateTime.of(2026, 3, 25, 12, 0);
    private static final UUID EVENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000099");

    @Mock
    private HoldManager holdManager;

    @Spy
    private RecordingLockManager lockManager = new RecordingLockManager();

    @Mock
    private SeatSelectionService seatSelectionService;

    @Mock
    private PerformanceSeatRepository performanceSeatRepository;

    @Mock
    private SeatStatusEventPublisher seatStatusEventPublisher;

    @Mock
    private HoldReleaseProgressRecorder progressRecorder;

    @InjectMocks
    private HoldReleaseTaskProcessor taskProcessor;

    @Test
    void recordsHoldReleaseBeforePublishingCurrentlyAvailableSeats() {
        final HoldReleaseTask task = task(false);
        when(seatSelectionService.getSelectingSeatIds(1L)).thenReturn(Set.of());
        when(holdManager.isHeld(1L, 10L)).thenReturn(false);
        when(holdManager.isHeld(1L, 20L)).thenReturn(false);
        stubPerformanceSeats();

        taskProcessor.process(EVENT_ID, task, FIXED_NOW);

        final InOrder inOrder = inOrder(holdManager, progressRecorder, seatStatusEventPublisher);
        inOrder.verify(holdManager).release(1L, "old-hold", List.of(10L, 20L));
        inOrder.verify(progressRecorder).recordHoldReleased(EVENT_ID, FIXED_NOW);
        inOrder.verify(seatStatusEventPublisher).publish(1L, 910L, SeatStatusAction.RELEASED);
        inOrder.verify(seatStatusEventPublisher).publish(1L, 920L, SeatStatusAction.RELEASED);
    }

    @Test
    void retrySkipsRedisReleaseAndRepublishesWhenSeatsAreStillAvailable() {
        final HoldReleaseTask task = task(true);
        when(seatSelectionService.getSelectingSeatIds(1L)).thenReturn(Set.of());
        when(holdManager.isHeld(1L, 10L)).thenReturn(false);
        when(holdManager.isHeld(1L, 20L)).thenReturn(false);
        stubPerformanceSeats();

        taskProcessor.process(EVENT_ID, task, FIXED_NOW.plusSeconds(30));

        verify(holdManager, never()).release(1L, "old-hold", List.of(10L, 20L));
        verify(progressRecorder, never()).recordHoldReleased(EVENT_ID, FIXED_NOW.plusSeconds(30));
        verify(seatStatusEventPublisher).publish(1L, 910L, SeatStatusAction.RELEASED);
        verify(seatStatusEventPublisher).publish(1L, 920L, SeatStatusAction.RELEASED);
    }

    @Test
    void retryDoesNotPublishOverANewerHoldOrSelection() {
        final HoldReleaseTask task = task(true);
        when(seatSelectionService.getSelectingSeatIds(1L)).thenReturn(Set.of(20L));
        when(holdManager.isHeld(1L, 10L)).thenReturn(true);
        when(holdManager.isHeld(1L, 20L)).thenReturn(false);

        taskProcessor.process(EVENT_ID, task, FIXED_NOW.plusSeconds(30));

        verifyNoInteractions(seatStatusEventPublisher);
    }

    @Test
    void publicationFailureCanBeRetriedAfterReleaseWasPersisted() {
        final HoldReleaseTask firstAttempt = task(false);
        final HoldReleaseTask retry = task(true);
        when(seatSelectionService.getSelectingSeatIds(1L)).thenReturn(Set.of());
        when(holdManager.isHeld(1L, 10L)).thenReturn(false);
        when(holdManager.isHeld(1L, 20L)).thenReturn(false);
        stubPerformanceSeats();
        doThrow(new RuntimeException("publish failed"))
                .doNothing()
                .when(seatStatusEventPublisher).publish(1L, 910L, SeatStatusAction.RELEASED);

        assertThatThrownBy(() -> taskProcessor.process(EVENT_ID, firstAttempt, FIXED_NOW))
                .hasMessage("publish failed");
        taskProcessor.process(EVENT_ID, retry, FIXED_NOW.plusSeconds(30));

        verify(holdManager, times(1)).release(1L, "old-hold", List.of(10L, 20L));
        verify(progressRecorder, times(1)).recordHoldReleased(EVENT_ID, FIXED_NOW);
        verify(seatStatusEventPublisher, times(2)).publish(1L, 910L, SeatStatusAction.RELEASED);
        verify(seatStatusEventPublisher, times(1)).publish(1L, 920L, SeatStatusAction.RELEASED);
    }

    private void stubPerformanceSeats() {
        when(performanceSeatRepository.findAllByPerformanceIdAndSeatIdIn(1L, List.of(10L, 20L)))
                .thenReturn(List.of(performanceSeat(10L, 910L), performanceSeat(20L, 920L)));
    }

    private PerformanceSeat performanceSeat(final long seatId, final long performanceSeatId) {
        PerformanceSeat seat = new PerformanceSeat(1L, seatId, 30L, PerformanceSeatState.AVAILABLE, BigDecimal.TEN);
        ReflectionTestUtils.setField(seat, "id", performanceSeatId);
        return seat;
    }

    @Test
    void holdsSeatLocksAcrossReleaseAndPublication() {
        taskProcessor.process(EVENT_ID, task(false), LocalDateTime.of(2026, 3, 15, 12, 0));

        assertThat(lockManager.lastAcquisition().keys())
                .containsExactly(LockKey.seat(1L, 10L), LockKey.seat(1L, 20L));
    }

    private HoldReleaseTask task(final boolean holdReleased) {
        return new HoldReleaseTask(1L, "old-hold", List.of(10L, 20L), holdReleased);
    }
}
