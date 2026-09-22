package com.ticket.show.usecase;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ticket.show.api.PerformanceLayoutSnapshot;
import com.ticket.show.api.PerformanceVenueLayoutCatalogApi;
import com.ticket.show.domain.Grade;
import com.ticket.show.domain.GradeRepository;
import com.ticket.show.domain.performance.Performance;
import com.ticket.show.domain.performance.PerformanceGrade;
import com.ticket.show.domain.performance.PerformanceRepository;
import com.ticket.show.domain.show.Show;
import com.ticket.show.domain.show.ShowRepository;
import com.ticket.show.exception.PerformanceNotFoundException;
import com.ticket.show.exception.ShowNotFoundException;
import com.ticket.venue.api.VenueLookupApi;
import com.ticket.venue.api.VenueSeatLookupApi;
import com.ticket.venue.api.VenueSeatSnapshot;
import com.ticket.venue.api.VenueSnapshot;

import lombok.RequiredArgsConstructor;

/**
 * {@link PerformanceVenueLayoutCatalogApi}의 show 소유 구현이다. 회차 정적 seat-map에 필요한 venue·좌석 좌표·등급 표시값을 한 번에 조회해 booking에게
 * scalar snapshot만 넘긴다.
 *
 * <p>venue 조합(venue 이름·seat-map 좌표·좌석 배치)은 이 application 계층이 한다 — local 조회({@code PerformanceRepository})는 show 자기 DB만
 * 본다.
 */
@Service
@RequiredArgsConstructor
public class PerformanceVenueLayoutCatalogService implements PerformanceVenueLayoutCatalogApi {
    private final PerformanceRepository performanceRepository;
    private final GradeRepository gradeRepository;
    private final ShowRepository showRepository;
    private final VenueLookupApi venueLookup;
    private final VenueSeatLookupApi venueSeatLookup;

    @Override
    @Transactional(readOnly = true)
    public PerformanceLayoutSnapshot getVenueLayout(final long performanceId) {
        final Performance performance = performanceRepository
                .findById(performanceId)
                .orElseThrow(() -> new PerformanceNotFoundException(performanceId));
        final Show show = showRepository
                .findById(performance.getShowId())
                .orElseThrow(() -> new ShowNotFoundException(performance.getShowId()));
        final Long venueId = show.getVenueId();

        final VenueSnapshot venue = venueLookup.getVenueSnapshot(venueId);

        final Map<Long, PerformanceLayoutSnapshot.SeatLayout> seatLayoutBySeatId =
                venueSeatLookup.findAllSeatLayouts(venueId).stream()
                        .collect(Collectors.toMap(VenueSeatSnapshot::seatId, this::toSeatLayout));

        final Map<Long, PerformanceLayoutSnapshot.GradeLayout> gradeLayoutByPerformanceGradeId =
                toGradeLayouts(performanceId);

        return new PerformanceLayoutSnapshot(
                performanceId,
                venueId,
                venue.name(),
                venue.seatMapLayout().viewBoxWidth(),
                venue.seatMapLayout().viewBoxHeight(),
                venue.seatMapLayout().seatDiameter(),
                seatLayoutBySeatId,
                gradeLayoutByPerformanceGradeId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Long> findRepresentativePerformanceId(final long showId) {
        return performanceRepository.findRepresentativePerformanceIdByShowId(showId);
    }

    /**
     * 회차에 배정된 PerformanceGrade를 <b>하나도 빠뜨리지 않고</b> 담는다 — booking은 이 map을 완전한 것으로 보고 좌석 편성·주문 표시값을 만든다. 대응 Grade가 없는 편성은
     * {@code fk_performance_grades_grade}가 막는 데이터 깨짐이라 조용히 빼지 않고 여기서 터진다.
     */
    private Map<Long, PerformanceLayoutSnapshot.GradeLayout> toGradeLayouts(final long performanceId) {
        final List<PerformanceGrade> performanceGrades = performanceRepository.findPerformanceGrades(performanceId);
        final Map<Long, Grade> gradesById = gradeRepository.findGradeNames(
                performanceGrades.stream().map(PerformanceGrade::getGradeId).collect(Collectors.toSet()));

        return performanceGrades.stream()
                .collect(Collectors.toMap(
                        PerformanceGrade::getId,
                        performanceGrade -> toGradeLayout(performanceGrade, gradeOf(gradesById, performanceGrade))));
    }

    private static Grade gradeOf(final Map<Long, Grade> gradesById, final PerformanceGrade performanceGrade) {
        return Objects.requireNonNull(
                gradesById.get(performanceGrade.getGradeId()),
                () -> "PerformanceGrade %d의 Grade를 찾을 수 없습니다: gradeId=%d"
                        .formatted(performanceGrade.getId(), performanceGrade.getGradeId()));
    }

    private PerformanceLayoutSnapshot.GradeLayout toGradeLayout(
            final PerformanceGrade performanceGrade, final Grade grade) {
        return new PerformanceLayoutSnapshot.GradeLayout(
                performanceGrade.getId(), grade.getCode(), grade.getName(), performanceGrade.getSortOrder());
    }

    private PerformanceLayoutSnapshot.SeatLayout toSeatLayout(final VenueSeatSnapshot layout) {
        return new PerformanceLayoutSnapshot.SeatLayout(
                layout.seatId(),
                layout.floor(),
                layout.section(),
                layout.rowNo(),
                layout.seatNo(),
                layout.x(),
                layout.y());
    }
}
