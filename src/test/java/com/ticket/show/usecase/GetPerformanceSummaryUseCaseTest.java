package com.ticket.show.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ticket.shared.exception.NotFoundException;
import com.ticket.show.domain.performance.Performance;
import com.ticket.show.domain.performance.PerformanceRepository;
import com.ticket.show.domain.show.Show;
import com.ticket.show.domain.show.ShowRepository;
import com.ticket.venue.api.Region;
import com.ticket.venue.api.VenueLookupApi;
import com.ticket.venue.api.VenueSnapshot;

@SuppressWarnings("NonAsciiCharacters")
@ExtendWith(MockitoExtension.class)
class GetPerformanceSummaryUseCaseTest {
    @Mock
    private PerformanceRepository performanceRepository;

    @Mock
    private ShowRepository showRepository;

    @Mock
    private VenueLookupApi venueLookup;

    @InjectMocks
    private GetPerformanceSummaryUseCase useCase;

    @Test
    void 공연_요약정보를_반환한다() {
        LocalDateTime startTime = LocalDateTime.of(2026, 3, 20, 19, 30);
        final Performance performance = performance(7L, startTime);
        final Show show = show(7L, "싱어게인", 5L);
        when(performanceRepository.findById(1L)).thenReturn(Optional.of(performance));
        when(showRepository.findById(7L)).thenReturn(Optional.of(show));
        when(venueLookup.findVenueSnapshot(5L))
                .thenReturn(Optional.of(new VenueSnapshot(
                        5L,
                        "venue",
                        "주소",
                        Region.CHUNGCHEONG,
                        null,
                        null,
                        null,
                        null,
                        new VenueSnapshot.SeatMapLayout(0, 0, 0.0))));

        GetPerformanceSummaryUseCase.Output output = useCase.execute(new GetPerformanceSummaryUseCase.Input(1L));

        assertThat(output.title()).isEqualTo("싱어게인");
        assertThat(output.region()).isEqualTo("충청");
        assertThat(output.startTime()).isEqualTo(startTime);
    }

    @Test
    void 공연과_연결되지_않은_회차면_예외를_던진다() {
        when(performanceRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(new GetPerformanceSummaryUseCase.Input(1L)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void 공연장이_없어도_지역은_null로_반환한다() {
        LocalDateTime startTime = LocalDateTime.of(2026, 3, 20, 19, 30);
        final Performance performance = performance(7L, startTime);
        final Show show = show(7L, "싱어게인", null);
        when(performanceRepository.findById(1L)).thenReturn(Optional.of(performance));
        when(showRepository.findById(7L)).thenReturn(Optional.of(show));

        GetPerformanceSummaryUseCase.Output output = useCase.execute(new GetPerformanceSummaryUseCase.Input(1L));

        assertThat(output.region()).isNull();
    }

    private static Performance performance(final long showId, final LocalDateTime startTime) {
        final Performance performance = mock(Performance.class);
        lenient().when(performance.getShowId()).thenReturn(showId);
        lenient().when(performance.getStartTime()).thenReturn(startTime);
        return performance;
    }

    private static Show show(final long showId, final String title, final Long venueId) {
        final Show show = mock(Show.class);
        lenient().when(show.getId()).thenReturn(showId);
        lenient().when(show.getTitle()).thenReturn(title);
        lenient().when(show.getVenueId()).thenReturn(venueId);
        return show;
    }
}
