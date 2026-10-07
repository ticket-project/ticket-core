package com.ticket.booking.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ticket.booking.concurrency.LockKey;
import com.ticket.booking.concurrency.RecordingDistributedLock;
import com.ticket.booking.hold.domain.HoldRegistry;
import com.ticket.booking.order.usecase.OrderHoldSnapshot;
import com.ticket.booking.seat.domain.SeatOccupancy;
import com.ticket.booking.seat.port.SeatStatusEvent.SeatStatusAction;
import com.ticket.booking.seat.port.SeatStatusEventPublisher;
import com.ticket.booking.selection.domain.SeatSelectionService;

@ExtendWith(MockitoExtension.class)
class HoldReleaserTest {
    @Mock
    private HoldRegistry holdRegistry;

    private final RecordingDistributedLock distributedLock = new RecordingDistributedLock();

    @Mock
    private SeatSelectionService seatSelectionService;

    @Mock
    private SeatStatusEventPublisher seatStatusEventPublisher;

    private HoldReleaser releaser;

    @BeforeEach
    void setUp() {
        releaser = new HoldReleaser(
                distributedLock,
                holdRegistry,
                new SeatOccupancy(seatSelectionService, holdRegistry),
                seatStatusEventPublisher);
    }

    @Test
    void releasesHoldBeforePublishingCurrentlyAvailableSeats() {
        when(holdRegistry.isHeld(1L, 10L)).thenReturn(false);
        when(holdRegistry.isHeld(1L, 20L)).thenReturn(false);

        releaser.releaseAndPublish("old-hold", snapshot());

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

        releaser.releaseAndPublish("old-hold", snapshot());

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
        doThrow(new RuntimeException("publish failed"))
                .doNothing()
                .when(seatStatusEventPublisher)
                .publish(1L, 910L, 10L, SeatStatusAction.RELEASED);

        assertThatThrownBy(() -> releaser.releaseAndPublish("old-hold", snapshot()))
                .hasMessage("publish failed");
        releaser.releaseAndPublish("old-hold", snapshot());

        verify(holdRegistry, times(2)).release(1L, "old-hold", List.of(10L, 20L));
        verify(seatStatusEventPublisher, times(2)).publish(1L, 910L, 10L, SeatStatusAction.RELEASED);
        verify(seatStatusEventPublisher, times(1)).publish(1L, 920L, 20L, SeatStatusAction.RELEASED);
    }

    @Test
    void holdsSeatLocksAcrossReleaseAndPublication() {
        releaser.releaseAndPublish("old-hold", snapshot());

        assertThat(distributedLock.lastAcquisition().keys())
                .containsExactly(LockKey.seat(1L, 10L), LockKey.seat(1L, 20L));
    }

    private OrderHoldSnapshot snapshot() {
        return new OrderHoldSnapshot(1L, List.of(10L, 20L), Map.of(10L, 910L, 20L, 920L));
    }
}
