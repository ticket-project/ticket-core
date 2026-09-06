package com.ticket.catalog.application.publicapi;

import com.ticket.catalog.PerformanceSummary;
import com.ticket.catalog.VenueLayout;
import com.ticket.catalog.application.performance.query.PerformanceSummaryBatchReadRepository;
import com.ticket.catalog.application.show.query.ShowSummaryBatchReadRepository;
import com.ticket.catalog.domain.show.Show;
import com.ticket.catalog.domain.show.Venue;
import com.ticket.catalog.domain.show.repository.ShowRepository;
import com.ticket.error.NotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
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
    void 회차_표시값_배치를_조회한다() {
        Map<Long, PerformanceSummary> summaries = Map.of(
                200L, new PerformanceSummary(200L, 100L, 3L, LocalDateTime.of(2026, 3, 20, 19, 30))
        );
        when(performanceSummaryBatchReadRepository.findSummaries(Set.of(200L))).thenReturn(summaries);

        assertThat(service.getPerformanceSummaries(Set.of(200L))).isEqualTo(summaries);
    }
}
