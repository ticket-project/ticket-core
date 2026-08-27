package com.ticket.core.app.order.command;

import com.ticket.core.app.event.HoldReleaseProgressRecorder;
import com.ticket.core.app.order.command.HoldReleaseTask;
import com.ticket.core.domain.hold.command.HoldManager;
import com.ticket.core.domain.performanceseat.command.SeatSelectionService;
import com.ticket.core.domain.performanceseat.command.SeatStatusPublisher;
import com.ticket.core.app.lock.LockKey;
import com.ticket.core.app.lock.RecordingLockManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

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

    @Mock
    private HoldManager holdManager;

    @Spy
    private RecordingLockManager lockManager = new RecordingLockManager();

    @Mock
    private SeatSelectionService seatSelectionService;

    @Mock
    private SeatStatusPublisher seatStatusPublisher;

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

        taskProcessor.process(99L, task, FIXED_NOW);

        final InOrder inOrder = inOrder(holdManager, progressRecorder, seatStatusPublisher);
        inOrder.verify(holdManager).release(1L, "old-hold", List.of(10L, 20L));
        inOrder.verify(progressRecorder).recordHoldReleased(99L, FIXED_NOW);
        inOrder.verify(seatStatusPublisher).publishReleased(1L, List.of(10L, 20L));
    }

    @Test
    void retrySkipsRedisReleaseAndRepublishesWhenSeatsAreStillAvailable() {
        final HoldReleaseTask task = task(true);
        when(seatSelectionService.getSelectingSeatIds(1L)).thenReturn(Set.of());
        when(holdManager.isHeld(1L, 10L)).thenReturn(false);
        when(holdManager.isHeld(1L, 20L)).thenReturn(false);

        taskProcessor.process(99L, task, FIXED_NOW.plusSeconds(30));

        verify(holdManager, never()).release(1L, "old-hold", List.of(10L, 20L));
        verify(progressRecorder, never()).recordHoldReleased(99L, FIXED_NOW.plusSeconds(30));
        verify(seatStatusPublisher).publishReleased(1L, List.of(10L, 20L));
    }

    @Test
    void retryDoesNotPublishOverANewerHoldOrSelection() {
        final HoldReleaseTask task = task(true);
        when(seatSelectionService.getSelectingSeatIds(1L)).thenReturn(Set.of(20L));
        when(holdManager.isHeld(1L, 10L)).thenReturn(true);
        when(holdManager.isHeld(1L, 20L)).thenReturn(false);

        taskProcessor.process(99L, task, FIXED_NOW.plusSeconds(30));

        verifyNoInteractions(seatStatusPublisher);
    }

    @Test
    void publicationFailureCanBeRetriedAfterReleaseWasPersisted() {
        final HoldReleaseTask firstAttempt = task(false);
        final HoldReleaseTask retry = task(true);
        when(seatSelectionService.getSelectingSeatIds(1L)).thenReturn(Set.of());
        when(holdManager.isHeld(1L, 10L)).thenReturn(false);
        when(holdManager.isHeld(1L, 20L)).thenReturn(false);
        doThrow(new RuntimeException("publish failed"))
                .doNothing()
                .when(seatStatusPublisher).publishReleased(1L, List.of(10L, 20L));

        assertThatThrownBy(() -> taskProcessor.process(99L, firstAttempt, FIXED_NOW))
                .hasMessage("publish failed");
        taskProcessor.process(99L, retry, FIXED_NOW.plusSeconds(30));

        verify(holdManager, times(1)).release(1L, "old-hold", List.of(10L, 20L));
        verify(progressRecorder, times(1)).recordHoldReleased(99L, FIXED_NOW);
        verify(seatStatusPublisher, times(2)).publishReleased(1L, List.of(10L, 20L));
    }

    @Test
    void holdsSeatLocksAcrossReleaseAndPublication() {
        taskProcessor.process(1L, task(false), LocalDateTime.of(2026, 3, 15, 12, 0));

        assertThat(lockManager.lastAcquisition().keys())
                .containsExactly(LockKey.seat(1L, 10L), LockKey.seat(1L, 20L));
    }

    private HoldReleaseTask task(final boolean holdReleased) {
        return new HoldReleaseTask(1L, "old-hold", List.of(10L, 20L), holdReleased);
    }
}
