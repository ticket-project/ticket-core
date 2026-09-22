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
    void venue의_좌석_좌표와_회차_grade를_조합한다() {
        final Performance performance = performance(2L, null);
        final Show show = show(2L, "show", 3L);
        when(performanceRepository.findById(1L)).thenReturn(Optional.of(performance));
        when(showRepository.findById(2L)).thenReturn(Optional.of(show));
        when(venueLookup.getVenueSnapshot(3L))
                .thenReturn((new VenueSnapshot(
                        3L,
                        "venue",
                        "주소",
                        new VenueSnapshot.RegionView("SEOUL", "서울"),
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

    /**
     * 대응 Grade가 없는 PerformanceGrade는 {@code fk_performance_grades_grade}가 막는 데이터 깨짐이다. 조용히 빼면 booking의 좌석 맵·잔여석 집계가 그
     * 등급만 통째로 빠진 채로 사용자에게 나간다 — 공개 계약이 "모든 PerformanceGrade를 담는다"이므로 여기서 터뜨린다.
     */
    @Test
    void 등급을_찾지_못한_편성이_있으면_layout을_만들지_않고_터진다() {
        final Performance performance = performance(2L, null);
        final Show show = show(2L, "show", 3L);
        when(performanceRepository.findById(1L)).thenReturn(Optional.of(performance));
        when(showRepository.findById(2L)).thenReturn(Optional.of(show));
        when(venueLookup.getVenueSnapshot(3L))
                .thenReturn(new VenueSnapshot(
                        3L, "공연장", null, null, null, null, null, null, new VenueSnapshot.SeatMapLayout(0, 0, 0.0)));
        final PerformanceGrade vip = performanceGrade(100L, 7L, new BigDecimal("170000"), 1);
        final PerformanceGrade dangling = performanceGrade(101L, 8L, new BigDecimal("120000"), 2);
        when(performanceRepository.findPerformanceGrades(1L)).thenReturn(List.of(vip, dangling));
        when(gradeRepository.findGradeNames(Set.of(7L, 8L))).thenReturn(Map.of(7L, Grade.of("VIP", "VIP석")));

        assertThatThrownBy(() -> service.getVenueLayout(1L))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("gradeId=8");
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
