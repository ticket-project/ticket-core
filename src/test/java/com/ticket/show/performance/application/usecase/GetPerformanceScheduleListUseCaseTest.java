package com.ticket.show.performance.application.usecase;

import com.ticket.show.performance.domain.Performance;
import com.ticket.show.performance.domain.PerformanceRepository;
import com.ticket.error.NotFoundException;
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
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
class GetPerformanceScheduleListUseCaseTest {

    @Mock
    private PerformanceRepository performanceRepository;

    @InjectMocks
    private GetPerformanceScheduleListUseCase useCase;

    @Test
    void 같은_공연의_회차_목록을_반환한다() {
        //given
        Performance selected = mock(Performance.class);
        Performance another = mock(Performance.class);
        LocalDateTime now = LocalDateTime.of(2026, 3, 15, 18, 0);

        when(selected.getId()).thenReturn(10L);
        when(selected.getShowId()).thenReturn(100L);
        when(selected.getPerformanceNo()).thenReturn(1L);
        when(selected.getStartTime()).thenReturn(now);
        when(another.getId()).thenReturn(11L);
        when(another.getPerformanceNo()).thenReturn(2L);
        when(another.getStartTime()).thenReturn(now.plusDays(1));

        when(performanceRepository.findById(10L)).thenReturn(Optional.of(selected));
        when(performanceRepository.findAllByShowIdOrderByStartTimeAscPerformanceNoAsc(100L)).thenReturn(List.of(selected, another));

        //when
        GetPerformanceScheduleListUseCase.Output output = useCase.execute(new GetPerformanceScheduleListUseCase.Input(10L));

        //then
        assertThat(output.showId()).isEqualTo(100L);
        assertThat(output.selectedPerformanceId()).isEqualTo(10L);
        assertThat(output.schedules()).hasSize(2);
    }

    @Test
    void 존재하지_않는_회차면_예외를_던진다() {
        //given
        when(performanceRepository.findById(10L)).thenReturn(Optional.empty());

        //when
        //then
        assertThatThrownBy(() -> useCase.execute(new GetPerformanceScheduleListUseCase.Input(10L)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void 같은_공연의_회차가_없으면_빈_목록을_반환한다() {
        //given
        Performance selected = mock(Performance.class);
        when(selected.getId()).thenReturn(10L);
        when(selected.getShowId()).thenReturn(100L);
        when(performanceRepository.findById(10L)).thenReturn(Optional.of(selected));
        when(performanceRepository.findAllByShowIdOrderByStartTimeAscPerformanceNoAsc(100L)).thenReturn(List.of());

        //when
        GetPerformanceScheduleListUseCase.Output output = useCase.execute(new GetPerformanceScheduleListUseCase.Input(10L));

        //then
        assertThat(output.showId()).isEqualTo(100L);
        assertThat(output.selectedPerformanceId()).isEqualTo(10L);
        assertThat(output.schedules()).isEmpty();
    }
}
