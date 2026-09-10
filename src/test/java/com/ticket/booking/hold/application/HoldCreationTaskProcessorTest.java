package com.ticket.booking.hold.application;

import com.ticket.booking.support.application.RecordingLockManager;
import com.ticket.booking.hold.domain.Hold;
import com.ticket.booking.hold.domain.HoldStore;
import com.ticket.booking.selection.domain.SeatSelectionService;
import com.ticket.booking.seat.domain.PerformanceSeat;
import com.ticket.booking.seat.domain.PerformanceSeatState;
import com.ticket.booking.seat.domain.PerformanceSeatRepository;
import com.ticket.booking.seat.application.SeatStatusEvent.SeatStatusAction;
import com.ticket.booking.seat.application.SeatStatusEventPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HoldCreationTaskProcessorTest {

    @Mock
    private HoldStore holdStore;

    @Mock
    private SeatSelectionService seatSelectionService;

    @Mock
    private PerformanceSeatRepository performanceSeatRepository;

    @Mock
    private SeatStatusEventPublisher seatStatusEventPublisher;

    @Test
    void current_hold_releases_only_the_owners_selection_before_publishing_held() {
        final Hold hold = hold();
        when(holdStore.isHeldBy(10L, 100L, "hold-key")).thenReturn(true);
        when(holdStore.isHeldBy(10L, 200L, "hold-key")).thenReturn(true);
        when(performanceSeatRepository.findAllByPerformanceIdAndSeatIdIn(10L, List.of(100L, 200L)))
                .thenReturn(List.of(performanceSeat(100L, 901L), performanceSeat(200L, 902L)));

        processor().process(hold);

        final InOrder inOrder = inOrder(holdStore, seatSelectionService, seatStatusEventPublisher);
        inOrder.verify(holdStore).isHeldBy(10L, 100L, "hold-key");
        inOrder.verify(holdStore).isHeldBy(10L, 200L, "hold-key");
        inOrder.verify(seatSelectionService).deselectIfOwned(10L, 100L, 20L);
        inOrder.verify(seatSelectionService).deselectIfOwned(10L, 200L, 20L);
        inOrder.verify(seatStatusEventPublisher).publish(10L, 901L, SeatStatusAction.HELD);
        inOrder.verify(seatStatusEventPublisher).publish(10L, 902L, SeatStatusAction.HELD);
    }

    @Test
    void stale_hold_does_not_release_a_new_selection_or_publish_held() {
        final Hold hold = hold();
        when(holdStore.isHeldBy(10L, 100L, "hold-key")).thenReturn(false);

        processor().process(hold);

        verify(holdStore).isHeldBy(10L, 100L, "hold-key");
        verify(holdStore, never()).isHeldBy(10L, 200L, "hold-key");
        verifyNoInteractions(seatSelectionService, seatStatusEventPublisher, performanceSeatRepository);
    }

    private PerformanceSeat performanceSeat(final long seatId, final long performanceSeatId) {
        PerformanceSeat seat = new PerformanceSeat(10L, seatId, 30L, PerformanceSeatState.AVAILABLE, BigDecimal.TEN);
        ReflectionTestUtils.setField(seat, "id", performanceSeatId);
        return seat;
    }

    private HoldCreationTaskProcessor processor() {
        return new HoldCreationTaskProcessor(
                new RecordingLockManager(), holdStore, seatSelectionService, performanceSeatRepository, seatStatusEventPublisher);
    }

    private Hold hold() {
        return new Hold(
                "hold-key",
                20L,
                10L,
                List.of(100L, 200L),
                LocalDateTime.of(2026, 3, 15, 12, 0)
        );
    }
}
