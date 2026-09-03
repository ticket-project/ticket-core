package com.ticket.catalog.internal.application.publicapi;

import com.ticket.catalog.PerformanceSummary;
import com.ticket.catalog.ShowSeatMapEntry;
import com.ticket.catalog.VenueLayout;
import com.ticket.catalog.internal.application.performance.query.PerformanceSummaryBatchReadRepository;
import com.ticket.catalog.internal.application.show.query.ShowSeatMapReadRepository;
import com.ticket.catalog.internal.application.show.query.ShowSummaryBatchReadRepository;
import com.ticket.catalog.internal.domain.show.Show;
import com.ticket.catalog.internal.domain.show.Venue;
import com.ticket.catalog.internal.domain.show.repository.ShowRepository;
import com.ticket.error.NotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
class ShowLookupServiceTest {

    @Mock
    private ShowRepository showRepository;

    @Mock
    private ShowSummaryBatchReadRepository showSummaryBatchReadRepository;

    @Mock
    private ShowSeatMapReadRepository showSeatMapReadRepository;

    @Mock
    private PerformanceSummaryBatchReadRepository performanceSummaryBatchReadRepository;

    @InjectMocks
    private ShowLookupService service;

    @Test
    void 공연장_레이아웃을_반환한다() {
        Show show = mock(Show.class);
        Venue venue = mock(Venue.class);
        when(showRepository.findById(100L)).thenReturn(Optional.of(show));
        when(show.getVenue()).thenReturn(venue);
        when(venue.getName()).thenReturn("올림픽홀");
        when(venue.getViewBoxWidth()).thenReturn(1000);
        when(venue.getViewBoxHeight()).thenReturn(800);
        when(venue.getSeatDiameter()).thenReturn(12.0);

        VenueLayout layout = service.getVenueLayout(100L);

        assertThat(layout).isEqualTo(new VenueLayout("올림픽홀", 1000, 800, 12.0));
    }

    @Test
    void 공연이_없으면_예외를_던진다() {
        when(showRepository.findById(100L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getVenueLayout(100L))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void 공연장_정보가_없으면_예외를_던진다() {
        Show show = mock(Show.class);
        when(showRepository.findById(100L)).thenReturn(Optional.of(show));
        when(show.getVenue()).thenReturn(null);

        assertThatThrownBy(() -> service.getVenueLayout(100L))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void 좌석_맵을_조회한다() {
        when(showRepository.existsById(100L)).thenReturn(true);
        List<ShowSeatMapEntry> seats = List.of(
                new ShowSeatMapEntry(1L, 1, "A", "10", "7", 10.0, 20.0, "VIP", "VIP", BigDecimal.TEN, 1)
        );
        when(showSeatMapReadRepository.findSeatMap(100L)).thenReturn(seats);

        assertThat(service.getSeatMap(100L)).isEqualTo(seats);
    }

    @Test
    void 존재하지_않는_공연의_좌석_맵을_조회하면_예외를_던진다() {
        when(showRepository.existsById(100L)).thenReturn(false);

        assertThatThrownBy(() -> service.getSeatMap(100L))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void 회차_표시값_배치를_조회한다() {
        Map<Long, PerformanceSummary> summaries = Map.of(
                200L, new PerformanceSummary(200L, 100L, 3L, LocalDateTime.of(2026, 3, 20, 19, 30))
        );
        when(performanceSummaryBatchReadRepository.findSummaries(Set.of(200L))).thenReturn(summaries);

        assertThat(service.getPerformanceSummaries(Set.of(200L))).isEqualTo(summaries);
    }
}
