package com.ticket.booking.application.usecase;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.ticket.booking.application.SeatSelectionCoordinator;
import com.ticket.booking.domain.seat.PerformanceSeat;
import com.ticket.booking.domain.seat.PerformanceSeatRepository;
import com.ticket.booking.domain.seat.PerformanceSeatState;
import com.ticket.booking.domain.selection.DeselectedSeatIds;
import com.ticket.booking.domain.selection.SeatSelectionService;
import com.ticket.member.MemberLookup;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
class DeselectAllSeatsUseCaseTest {
    @Mock private MemberLookup memberLookup;
    @Mock private SeatSelectionService seatSelectionService;
    @Mock private PerformanceSeatRepository performanceSeatRepository;
    @Mock private SeatSelectionCoordinator seatSelectionCoordinator;
    @InjectMocks private DeselectAllSeatsUseCase useCase;

    /**
     * 실제로 해제된 좌석만 알린다. 발행 자체는 coordinator가 좌석 락 안에서 현재 상태를 다시 확인한 뒤 하므로, 해제와 발행 사이에 남이 다시 선택한 좌석은
     * 걸러진다({@code SeatSelectionCoordinatorTest}).
     */
    @Test
    void 해제된_좌석마다_미리_조회한_performanceSeatId로_알린다() {
        when(seatSelectionService.deselectAll(10L, 1L))
                .thenReturn(DeselectedSeatIds.from(List.of(20L, 21L)));
        when(performanceSeatRepository.findAllByPerformanceIdAndSeatIdIn(10L, List.of(20L, 21L)))
                .thenReturn(List.of(performanceSeat(20L, 501L), performanceSeat(21L, 502L)));

        useCase.execute(new DeselectAllSeatsUseCase.Input(10L, 1L));

        verify(memberLookup).requireActive(1L);
        verify(seatSelectionService).deselectAll(10L, 1L);
        verify(seatSelectionCoordinator).notifyReleasedIfFree(10L, 20L, 501L);
        verify(seatSelectionCoordinator).notifyReleasedIfFree(10L, 21L, 502L);
        // 좌석마다 DB를 다시 읽지 않도록 한 번에 조회한 id를 넘긴다.
        verify(seatSelectionCoordinator, times(2))
                .notifyReleasedIfFree(
                        org.mockito.ArgumentMatchers.anyLong(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any());
        verifyNoMoreInteractions(seatSelectionCoordinator);
    }

    @Test
    void 해제된_좌석이_없으면_좌석을_조회하지도_알리지도_않는다() {
        when(seatSelectionService.deselectAll(10L, 1L))
                .thenReturn(DeselectedSeatIds.from(List.of()));

        useCase.execute(new DeselectAllSeatsUseCase.Input(10L, 1L));

        verify(performanceSeatRepository, times(0))
                .findAllByPerformanceIdAndSeatIdIn(
                        org.mockito.ArgumentMatchers.anyLong(),
                        org.mockito.ArgumentMatchers.anyList());
        verifyNoMoreInteractions(seatSelectionCoordinator);
    }

    private PerformanceSeat performanceSeat(final long seatId, final long performanceSeatId) {
        PerformanceSeat seat =
                new PerformanceSeat(
                        10L, seatId, 30L, PerformanceSeatState.AVAILABLE, BigDecimal.TEN);
        ReflectionTestUtils.setField(seat, "id", performanceSeatId);
        return seat;
    }
}
