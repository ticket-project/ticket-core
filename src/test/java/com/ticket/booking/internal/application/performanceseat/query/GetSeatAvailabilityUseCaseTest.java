package com.ticket.booking.internal.application.performanceseat.query;

import com.ticket.core.support.exception.CoreException;
import com.ticket.core.support.exception.ErrorType;
import com.ticket.booking.internal.domain.hold.command.HoldManager;
import com.ticket.catalog.internal.domain.performance.Performance;
import com.ticket.catalog.internal.domain.performance.repository.PerformanceRepository;
import com.ticket.booking.internal.domain.performanceseat.command.SeatSelectionService;
import com.ticket.catalog.internal.domain.show.Show;
import com.ticket.booking.internal.domain.performanceseat.model.PerformanceSeatState;
import com.ticket.booking.internal.application.performanceseat.query.model.AvailableSeatRow;
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
import java.util.Optional;

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
        List<AvailableSeatRow> rows =
                List.of(new AvailableSeatRow(1L, PerformanceSeatState.AVAILABLE, "VIP", 1));
        List<GetSeatAvailabilityUseCase.GradeAvailability> response =
                List.of(new GetSeatAvailabilityUseCase.GradeAvailability("VIP", 1, 0L));

        when(performanceRepository.findWithQueuePolicyById(10L)).thenReturn(Optional.of(performance));
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
        when(performanceRepository.findWithQueuePolicyById(10L)).thenReturn(Optional.of(performance));
        when(performance.getShow()).thenReturn(null);

        //when
        //then
        assertThatThrownBy(() -> useCase.execute(new GetSeatAvailabilityUseCase.Input(10L)))
                .isInstanceOf(CoreException.class)
                .satisfies(exception -> assertThat(((CoreException) exception).getErrorType()).isEqualTo(ErrorType.NOT_FOUND_DATA));
    }
}
