package com.ticket.show.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ticket.shared.exception.NotFoundException;
import com.ticket.show.api.PerformanceLayoutSnapshot;
import com.ticket.show.domain.Grade;
import com.ticket.show.domain.GradeRepository;
import com.ticket.show.domain.performance.Performance;
import com.ticket.show.domain.performance.PerformanceGrade;
import com.ticket.show.domain.performance.PerformanceRepository;
import com.ticket.show.domain.show.Show;
import com.ticket.show.domain.show.ShowRepository;
import com.ticket.venue.api.Region;
import com.ticket.venue.api.VenueLookupApi;
import com.ticket.venue.api.VenueSeatLookupApi;
import com.ticket.venue.api.VenueSeatSnapshot;
import com.ticket.venue.api.VenueSnapshot;

@SuppressWarnings("NonAsciiCharacters")
@ExtendWith(MockitoExtension.class)
class PerformanceVenueLayoutCatalogServiceTest {
    @Mock
    private PerformanceRepository performanceRepository;

    @Mock
    private ShowRepository showRepository;

    @Mock
    private GradeRepository gradeRepository;

    @Mock
    private VenueLookupApi venueLookup;

    @Mock
    private VenueSeatLookupApi venueSeatLookup;

    @InjectMocks
    private PerformanceVenueLayoutCatalogService service;

    @Test
    void 공연의_첫_회차_ID를_조회한다() {
        when(performanceRepository.findRepresentativePerformanceIdByShowId(10L)).thenReturn(Optional.of(20L));

        assertThat(service.findRepresentativePerformanceId(10L)).contains(20L);
    }

    @Test
    void 존재하지_않는_회차면_NotFoundException을_던진다() {
        when(performanceRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getVenueLayout(1L)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void venue가_없는_show면_seatLayout이_빈_맵이다() {
        final Performance performance = performance(2L, null);
        final Show show = show(2L, "show", null);
        when(performanceRepository.findById(1L)).thenReturn(Optional.of(performance));
        when(showRepository.findById(2L)).thenReturn(Optional.of(show));
        when(performanceRepository.findPerformanceGrades(1L)).thenReturn(List.of());

        final PerformanceLayoutSnapshot layout = service.getVenueLayout(1L);

        assertThat(layout.seatLayoutBySeatId()).isEmpty();
    }

    @Test
    void venue의_좌석_좌표와_회차_grade를_조합한다() {
        final Performance performance = performance(2L, null);
        final Show show = show(2L, "show", 3L);
        when(performanceRepository.findById(1L)).thenReturn(Optional.of(performance));
        when(showRepository.findById(2L)).thenReturn(Optional.of(show));
        when(venueLookup.findVenueSnapshot(3L))
                .thenReturn(Optional.of(new VenueSnapshot(
                        3L,
                        "venue",
                        "주소",
                        Region.SEOUL,
                        null,
                        null,
                        null,
                        null,
                        new VenueSnapshot.SeatMapLayout(500, 356, 4.8))));
        when(venueSeatLookup.findAllSeatLayouts(3L))
                .thenReturn(List.of(new VenueSeatSnapshot(10L, 1, "가", "A", "1", 129.0, 101.0)));
        final PerformanceGrade vip = performanceGrade(100L, 7L, new BigDecimal("170000"), 1);
        when(performanceRepository.findPerformanceGrades(1L)).thenReturn(List.of(vip));
        when(gradeRepository.findGradeNames(Set.of(7L))).thenReturn(Map.of(7L, Grade.of("VIP", "VIP석")));

        final PerformanceLayoutSnapshot layout = service.getVenueLayout(1L);

        assertThat(layout.venueId()).isEqualTo(3L);
        assertThat(layout.viewBoxWidth()).isEqualTo(500);
        assertThat(layout.seatLayoutBySeatId()).containsKey(10L);
        assertThat(layout.seatLayoutBySeatId().get(10L).x()).isEqualTo(129.0);
        assertThat(layout.gradeLayoutByPerformanceGradeId().get(100L).gradeCode())
                .isEqualTo("VIP");
    }

    /** 옛 {@code join grade}가 inner join이라 조용히 빠뜨리던 동작을 조립 쪽에서 그대로 유지한다. */
    @Test
    void 등급_이름을_찾지_못한_편성은_layout에서_빠진다() {
        final Performance performance = performance(2L, null);
        final Show show = show(2L, "show", null);
        when(performanceRepository.findById(1L)).thenReturn(Optional.of(performance));
        when(showRepository.findById(2L)).thenReturn(Optional.of(show));
        final PerformanceGrade vip = performanceGrade(100L, 7L, new BigDecimal("170000"), 1);
        final PerformanceGrade dangling = performanceGrade(101L, 8L, new BigDecimal("120000"), 2);
        when(performanceRepository.findPerformanceGrades(1L)).thenReturn(List.of(vip, dangling));
        when(gradeRepository.findGradeNames(Set.of(7L, 8L))).thenReturn(Map.of(7L, Grade.of("VIP", "VIP석")));

        final PerformanceLayoutSnapshot layout = service.getVenueLayout(1L);

        assertThat(layout.gradeLayoutByPerformanceGradeId()).containsOnlyKeys(100L);
    }

    private static PerformanceGrade performanceGrade(
            final long id, final long gradeId, final BigDecimal price, final int sortOrder) {
        final PerformanceGrade performanceGrade = mock(PerformanceGrade.class);
        lenient().when(performanceGrade.getId()).thenReturn(id);
        lenient().when(performanceGrade.getGradeId()).thenReturn(gradeId);
        lenient().when(performanceGrade.getPrice()).thenReturn(price);
        lenient().when(performanceGrade.getSortOrder()).thenReturn(sortOrder);
        return performanceGrade;
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
