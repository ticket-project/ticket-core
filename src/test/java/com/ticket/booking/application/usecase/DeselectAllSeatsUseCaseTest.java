package com.ticket.booking.application.usecase;

import com.ticket.booking.application.SeatStatusEvent;

import com.ticket.member.MemberLookup;
import com.ticket.booking.domain.SeatSelectionService;
import com.ticket.booking.domain.DeselectedSeatIds;
import com.ticket.booking.domain.PerformanceSeat;
import com.ticket.booking.domain.PerformanceSeatState;
import com.ticket.booking.domain.PerformanceSeatRepository;
import com.ticket.booking.application.SeatStatusEvent.SeatStatusAction;
import com.ticket.booking.application.SeatStatusEventPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
class DeselectAllSeatsUseCaseTest {

    @Mock
    private MemberLookup memberLookup;

    @Mock
    private SeatSelectionService seatSelectionService;

    @Mock
    private PerformanceSeatRepository performanceSeatRepository;

    @Mock
    private SeatStatusEventPublisher seatEventPublisher;

    @InjectMocks
    private DeselectAllSeatsUseCase useCase;

    @Test
    void deselect_all_then_publish_each_seat_with_performanceSeatId() {
        when(seatSelectionService.deselectAll(10L, 1L)).thenReturn(DeselectedSeatIds.from(List.of(20L, 21L)));
        when(performanceSeatRepository.findAllByPerformanceIdAndSeatIdIn(10L, List.of(20L, 21L)))
                .thenReturn(List.of(performanceSeat(20L, 501L), performanceSeat(21L, 502L)));

        useCase.execute(new DeselectAllSeatsUseCase.Input(10L, 1L));

        verify(memberLookup).requireActive(1L);
        verify(seatSelectionService).deselectAll(10L, 1L);
        verify(seatEventPublisher).publish(10L, 501L, SeatStatusAction.DESELECTED);
        verify(seatEventPublisher).publish(10L, 502L, SeatStatusAction.DESELECTED);
        verify(seatEventPublisher, times(2)).publish(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    private PerformanceSeat performanceSeat(final long seatId, final long performanceSeatId) {
        PerformanceSeat seat = new PerformanceSeat(10L, seatId, 30L, PerformanceSeatState.AVAILABLE, BigDecimal.TEN);
        ReflectionTestUtils.setField(seat, "id", performanceSeatId);
        return seat;
    }
}
