package com.ticket.booking.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.ticket.booking.concurrency.LockKey;
import com.ticket.booking.concurrency.RecordingDistributedLock;
import com.ticket.booking.hold.domain.HoldRegistry;
import com.ticket.booking.seat.domain.PerformanceSeat;
import com.ticket.booking.seat.domain.PerformanceSeatRepository;
import com.ticket.booking.seat.domain.PerformanceSeatState;
import com.ticket.booking.seat.domain.SeatOccupancy;
import com.ticket.booking.seat.port.SeatStatusEvent.SeatStatusAction;
import com.ticket.booking.seat.port.SeatStatusEventPublisher;
import com.ticket.booking.selection.domain.SeatSelectionService;

@ExtendWith(MockitoExtension.class)
class HoldReleaseCoordinatorTest {
    @Mock
    private HoldRegistry holdRegistry;

    private final RecordingDistributedLock distributedLock = new RecordingDistributedLock();

    @Mock
    private SeatSelectionService seatSelectionService;

    @Mock
    private PerformanceSeatRepository performanceSeatRepository;

    @Mock
    private SeatStatusEventPublisher seatStatusEventPublisher;

    private HoldReleaseCoordinator coordinator;

    @BeforeEach
    void setUp() {
        coordinator = new HoldReleaseCoordinator(
                distributedLock,
                holdRegistry,
                new SeatOccupancy(seatSelectionService, holdRegistry),
                performanceSeatRepository,
                seatStatusEventPublisher);
    }

    @Test
    void releasesHoldBeforePublishingCurrentlyAvailableSeats() {
        when(holdRegistry.isHeld(1L, 10L)).thenReturn(false);
        when(holdRegistry.isHeld(1L, 20L)).thenReturn(false);
        stubPerformanceSeats();

        coordinator.releaseAndPublish(task());

        final InOrder inOrder = inOrder(holdRegistry, seatStatusEventPublisher);
        inOrder.verify(holdRegistry).release(1L, "old-hold", List.of(10L, 20L));
        inOrder.verify(seatStatusEventPublisher).publish(1L, 910L, 10L, SeatStatusAction.RELEASED);
        inOrder.verify(seatStatusEventPublisher).publish(1L, 920L, 20L, SeatStatusAction.RELEASED);
    }

    @Test
    void doesNotPublishOverANewerHoldOrSelection() {
        when(seatSelectionService.isSelected(1L, 10L)).thenReturn(false);
        when(holdRegistry.isHeld(1L, 10L)).thenReturn(true);
        when(seatSelectionService.isSelected(1L, 20L)).thenReturn(true);

        coordinator.releaseAndPublish(task());

        verifyNoInteractions(seatStatusEventPublisher);
    }

    /**
     * 발행이 실패해 이벤트가 재전달되면 해제부터 다시 수행한다. 해제는 좌석에 아직 이 holdKey가 남아 있을 때만 지우므로 반복해도 안전하다 ({@code RedissonHoldStoreTest}가 그
     * 소유권 확인을 고정한다).
     */
    @Test
    void publicationFailureIsRetriedFromRelease() {
        when(holdRegistry.isHeld(1L, 10L)).thenReturn(false);
        when(holdRegistry.isHeld(1L, 20L)).thenReturn(false);
        stubPerformanceSeats();
        doThrow(new RuntimeException("publish failed"))
                .doNothing()
                .when(seatStatusEventPublisher)
                .publish(1L, 910L, 10L, SeatStatusAction.RELEASED);

        assertThatThrownBy(() -> coordinator.releaseAndPublish(task())).hasMessage("publish failed");
        coordinator.releaseAndPublish(task());

        verify(holdRegistry, times(2)).release(1L, "old-hold", List.of(10L, 20L));
        verify(seatStatusEventPublisher, times(2)).publish(1L, 910L, 10L, SeatStatusAction.RELEASED);
        verify(seatStatusEventPublisher, times(1)).publish(1L, 920L, 20L, SeatStatusAction.RELEASED);
    }

    @Test
    void holdsSeatLocksAcrossReleaseAndPublication() {
        coordinator.releaseAndPublish(task());

        assertThat(distributedLock.lastAcquisition().keys())
                .containsExactly(LockKey.seat(1L, 10L), LockKey.seat(1L, 20L));
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

    private HoldReleaseTask task() {
        return new HoldReleaseTask(1L, "old-hold", List.of(10L, 20L));
    }
}
