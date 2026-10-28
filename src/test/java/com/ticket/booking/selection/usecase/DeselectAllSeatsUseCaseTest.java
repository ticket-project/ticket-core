package com.ticket.booking.selection.usecase;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.ticket.booking.seat.domain.PerformanceSeat;
import com.ticket.booking.seat.domain.PerformanceSeatRepository;
import com.ticket.booking.seat.domain.PerformanceSeatState;
import com.ticket.booking.selection.domain.SeatSelectionService;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
class DeselectAllSeatsUseCaseTest {
    @Mock
    private SeatSelectionService seatSelectionService;

    @Mock
    private PerformanceSeatRepository performanceSeatRepository;

    @Mock
    private SeatSelectionWriter seatSelectionWriter;

    @InjectMocks
    private DeselectAllSeatsUseCase useCase;

    /** 선택 중인 좌석마다 미리 한 번에 조회한 performanceSeatId로 해제를 넘긴다. 해제와 발행은 좌석마다 저장소 스크립트 하나다. */
    @Test
    void 선택_중인_좌석마다_미리_조회한_performanceSeatId로_해제한다() {
        when(seatSelectionService.getSelectedSeatIds(10L, 1L)).thenReturn(Set.of(21L, 20L));
        when(performanceSeatRepository.findAllByPerformanceIdAndSeatIdIn(10L, List.of(20L, 21L)))
                .thenReturn(List.of(performanceSeat(20L, 501L), performanceSeat(21L, 502L)));

        useCase.execute(new DeselectAllSeatsUseCase.Input(10L, 1L));

        verify(seatSelectionWriter).deselectIfOwned(10L, 20L, 1L, 501L);
        verify(seatSelectionWriter).deselectIfOwned(10L, 21L, 1L, 502L);
        verifyNoMoreInteractions(seatSelectionWriter);
    }

    @Test
    void 선택_중인_좌석이_없으면_좌석을_조회하지도_해제하지도_않는다() {
        when(seatSelectionService.getSelectedSeatIds(10L, 1L)).thenReturn(Set.of());

        useCase.execute(new DeselectAllSeatsUseCase.Input(10L, 1L));

        verify(performanceSeatRepository, times(0))
                .findAllByPerformanceIdAndSeatIdIn(
                        org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyList());
        verifyNoMoreInteractions(seatSelectionWriter);
    }

    private PerformanceSeat performanceSeat(final long seatId, final long performanceSeatId) {
        PerformanceSeat seat = new PerformanceSeat(10L, seatId, 30L, PerformanceSeatState.AVAILABLE, BigDecimal.TEN);
        ReflectionTestUtils.setField(seat, "id", performanceSeatId);
        return seat;
    }
}
