package com.ticket.core.domain.performance.query;

import com.ticket.core.domain.performance.model.Performance;
import com.ticket.core.domain.performance.repository.PerformanceRepository;
import com.ticket.core.domain.show.model.Show;
import com.ticket.support.error.CoreException;
import com.ticket.support.error.ErrorType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
class GetPerformanceScheduleListUseCaseTest {

    @Mock
    private PerformanceFinder performanceFinder;

    @Mock
    private PerformanceRepository performanceRepository;

    @InjectMocks
    private GetPerformanceScheduleListUseCase useCase;

    @Test
    void 같은_공연의_회차_목록을_반환한다() {
        //given
        Show show = mock(Show.class);
        Performance selected = mock(Performance.class);
        Performance another = mock(Performance.class);
        LocalDateTime now = LocalDateTime.of(2026, 3, 15, 18, 0);

        when(show.getId()).thenReturn(100L);
        when(selected.getId()).thenReturn(10L);
        when(selected.getShow()).thenReturn(show);
        when(selected.getPerformanceNo()).thenReturn(1L);
        when(selected.getStartTime()).thenReturn(now);
        when(another.getId()).thenReturn(11L);
        when(another.getPerformanceNo()).thenReturn(2L);
        when(another.getStartTime()).thenReturn(now.plusDays(1));

        when(performanceFinder.findById(10L)).thenReturn(selected);
        when(performanceRepository.findAllByShowIdOrderByStartTimeAscPerformanceNoAsc(100L)).thenReturn(List.of(selected, another));

        //when
        GetPerformanceScheduleListUseCase.Output output = useCase.execute(new GetPerformanceScheduleListUseCase.Input(10L));

        //then
        assertThat(output.showId()).isEqualTo(100L);
        assertThat(output.selectedPerformanceId()).isEqualTo(10L);
        assertThat(output.schedules()).hasSize(2);
    }

    @Test
    void 공연이_연결되지_않은_회차면_예외를_던진다() {
        //given
        Performance performance = mock(Performance.class);
        when(performance.getShow()).thenReturn(null);
        when(performanceFinder.findById(10L)).thenReturn(performance);

        //when
        //then
        assertThatThrownBy(() -> useCase.execute(new GetPerformanceScheduleListUseCase.Input(10L)))
                .isInstanceOf(CoreException.class)
                .satisfies(exception -> assertThat(((CoreException) exception).getErrorType()).isEqualTo(ErrorType.NOT_FOUND_DATA));
    }

    @Test
    void 같은_공연의_회차가_없으면_빈_목록을_반환한다() {
        //given
        Show show = mock(Show.class);
        Performance selected = mock(Performance.class);
        when(show.getId()).thenReturn(100L);
        when(selected.getId()).thenReturn(10L);
        when(selected.getShow()).thenReturn(show);
        when(performanceFinder.findById(10L)).thenReturn(selected);
        when(performanceRepository.findAllByShowIdOrderByStartTimeAscPerformanceNoAsc(100L)).thenReturn(List.of());

        //when
        GetPerformanceScheduleListUseCase.Output output = useCase.execute(new GetPerformanceScheduleListUseCase.Input(10L));

        //then
        assertThat(output.showId()).isEqualTo(100L);
        assertThat(output.selectedPerformanceId()).isEqualTo(10L);
        assertThat(output.schedules()).isEmpty();
    }
}
