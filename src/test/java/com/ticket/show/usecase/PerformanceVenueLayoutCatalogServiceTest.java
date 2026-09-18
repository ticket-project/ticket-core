package com.ticket.show.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ticket.shared.exception.NotFoundException;
import com.ticket.show.api.PerformanceVenueLayout;
import com.ticket.show.domain.performance.PerformanceVenueLayoutContext;
import com.ticket.show.query.PerformanceVenueLayoutQuery;
import com.ticket.show.query.PerformanceVenueLayoutQuery.PerformanceGradeLayoutRow;
import com.ticket.venue.api.Region;
import com.ticket.venue.api.VenueLookupApi;
import com.ticket.venue.api.VenueSeatLayout;
import com.ticket.venue.api.VenueSeatLookupApi;
import com.ticket.venue.api.VenueSummary;

@SuppressWarnings("NonAsciiCharacters")
@ExtendWith(MockitoExtension.class)
class PerformanceVenueLayoutCatalogServiceTest {
    @Mock private PerformanceVenueLayoutQuery performanceVenueLayoutQuery;
    @Mock private VenueLookupApi venueLookup;
    @Mock private VenueSeatLookupApi venueSeatLookup;
    @InjectMocks private PerformanceVenueLayoutCatalogService service;

    @Test
    void 공연의_첫_회차_ID를_조회한다() {
        when(performanceVenueLayoutQuery.findRepresentativePerformanceIdByShowId(10L))
                .thenReturn(Optional.of(20L));

        assertThat(service.findRepresentativePerformanceId(10L)).contains(20L);
    }

    @Test
    void 존재하지_않는_회차면_NotFoundException을_던진다() {
        when(performanceVenueLayoutQuery.findVenueLayoutContext(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getVenueLayout(1L)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void venue가_없는_show면_seatLayout이_빈_맵이다() {
        when(performanceVenueLayoutQuery.findVenueLayoutContext(1L))
                .thenReturn(Optional.of(new PerformanceVenueLayoutContext(1L, null)));
        when(performanceVenueLayoutQuery.findGradeLayouts(1L)).thenReturn(List.of());

        final PerformanceVenueLayout layout = service.getVenueLayout(1L);

        assertThat(layout.seatLayoutBySeatId()).isEmpty();
    }

    @Test
    void venue의_좌석_좌표와_회차_grade를_조합한다() {
        when(performanceVenueLayoutQuery.findVenueLayoutContext(1L))
                .thenReturn(Optional.of(new PerformanceVenueLayoutContext(1L, 3L)));
        when(venueLookup.findSummary(3L))
                .thenReturn(
                        Optional.of(
                                new VenueSummary(
                                        3L,
                                        "venue",
                                        "주소",
                                        Region.SEOUL,
                                        null,
                                        null,
                                        null,
                                        null,
                                        new VenueSummary.SeatMapLayout(500, 356, 4.8))));
        when(venueSeatLookup.findAllSeatLayouts(3L))
                .thenReturn(List.of(new VenueSeatLayout(10L, 1, "가", "A", "1", 129.0, 101.0)));
        when(performanceVenueLayoutQuery.findGradeLayouts(1L))
                .thenReturn(List.of(new PerformanceGradeLayoutRow(100L, "VIP", "VIP석", 1)));

        final PerformanceVenueLayout layout = service.getVenueLayout(1L);

        assertThat(layout.venueId()).isEqualTo(3L);
        assertThat(layout.viewBoxWidth()).isEqualTo(500);
        assertThat(layout.seatLayoutBySeatId()).containsKey(10L);
        assertThat(layout.seatLayoutBySeatId().get(10L).x()).isEqualTo(129.0);
        assertThat(layout.gradeLayoutByPerformanceGradeId().get(100L).gradeCode()).isEqualTo("VIP");
    }
}
