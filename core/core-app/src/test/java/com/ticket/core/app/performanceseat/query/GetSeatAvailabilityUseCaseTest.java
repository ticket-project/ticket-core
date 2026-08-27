package com.ticket.core.app.performanceseat.query;

import com.ticket.support.error.CoreException;
import com.ticket.core.app.error.ApplicationErrorType;
import com.ticket.core.domain.hold.command.HoldManager;
import com.ticket.core.domain.performance.model.Performance;
import com.ticket.core.domain.performance.repository.PerformanceRepository;
import com.ticket.core.domain.performanceseat.command.SeatSelectionService;
import com.ticket.core.domain.show.model.Show;
import com.ticket.core.domain.performanceseat.model.PerformanceSeatState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
class GetSeatAvailabilityUseCaseTest {

    @Mock
    private PerformanceRepository performanceRepository;
    @Mock
    private SeatAvailabilityReadRepository seatAvailabilityReadRepository;
    @Mock
    private HoldManager holdManager;
    @Mock
    private SeatSelectionService seatSelectionService;
    @Mock
    private SeatAvailabilityCalculator seatAvailabilityCalculator;

    @InjectMocks
    private GetSeatAvailabilityUseCase useCase;

    @Test
    void DB와_redis_점유좌석을_합쳐_잔여석을_계산한다() {
        //given
        Performance performance = mock(Performance.class);
        Show show = mock(Show.class);
        List<SeatAvailabilityCalculator.AvailableSeatRow> rows =
                List.of(new SeatAvailabilityCalculator.AvailableSeatRow(1L, PerformanceSeatState.AVAILABLE, "VIP", 1));
        List<GetSeatAvailabilityUseCase.GradeAvailability> response =
                List.of(new GetSeatAvailabilityUseCase.GradeAvailability("VIP", 1, 0L));

        when(performanceRepository.getWithQueuePolicyById(10L)).thenReturn(performance);
        when(performance.getId()).thenReturn(10L);
        when(performance.getShow()).thenReturn(show);
        when(show.getId()).thenReturn(100L);
        when(seatAvailabilityReadRepository.findAvailableSeatRows(10L, 100L)).thenReturn(rows);
        when(seatSelectionService.getSelectingSeatIds(10L)).thenReturn(Set.of(1L));
        when(holdManager.getHoldingSeatIds(10L)).thenReturn(Set.of(2L));
        when(seatAvailabilityCalculator.calculate(rows, Set.of(1L, 2L))).thenReturn(response);

        //when
        GetSeatAvailabilityUseCase.Output output = useCase.execute(new GetSeatAvailabilityUseCase.Input(10L));

        //then
        assertThat(output.grades()).isEqualTo(response);
        verify(seatAvailabilityCalculator).calculate(rows, Set.of(1L, 2L));
    }

    @Test
    void 공연이_연결되지_않은_회차면_예외를_던진다() {
        //given
        Performance performance = mock(Performance.class);
        when(performanceRepository.getWithQueuePolicyById(10L)).thenReturn(performance);
        when(performance.getShow()).thenReturn(null);

        //when
        //then
        assertThatThrownBy(() -> useCase.execute(new GetSeatAvailabilityUseCase.Input(10L)))
                .isInstanceOf(CoreException.class)
                .satisfies(exception -> assertThat(((CoreException) exception).getErrorType()).isEqualTo(ApplicationErrorType.DATA_NOT_FOUND));
    }
}
