package com.ticket.show.usecase;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ticket.shared.exception.NotFoundException;
import com.ticket.show.api.PerformanceVenueLayout;
import com.ticket.show.api.PerformanceVenueLayoutCatalogApi;
import com.ticket.show.domain.Grade;
import com.ticket.show.domain.GradeRepository;
import com.ticket.show.domain.performance.Performance;
import com.ticket.show.domain.performance.PerformanceGrade;
import com.ticket.show.domain.performance.PerformanceRepository;
import com.ticket.show.domain.show.Show;
import com.ticket.show.domain.show.ShowRepository;
import com.ticket.venue.api.VenueLookupApi;
import com.ticket.venue.api.VenueSeatLayout;
import com.ticket.venue.api.VenueSeatLookupApi;
import com.ticket.venue.api.VenueSummary;

import lombok.RequiredArgsConstructor;

/**
 * {@link PerformanceVenueLayoutCatalogApi}의 show 소유 구현이다. 회차 정적 seat-map에 필요한 venue·좌석 좌표·등급 표시값을 한
 * 번에 조회해 booking에게 scalar snapshot만 넘긴다.
 *
 * <p>venue 조합(venue 이름·seat-map 좌표·좌석 배치)은 이 application 계층이 한다 — local 조회({@code
 * PerformanceRepository})는 show 자기 DB만 본다.
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
    public PerformanceVenueLayout getVenueLayout(final long performanceId) {
        final Performance performance =
                performanceRepository
                        .findById(performanceId)
                        .orElseThrow(
                                () -> new NotFoundException("공연을 찾을 수 없습니다. id=" + performanceId));
        final Show show =
                showRepository
                        .findById(performance.getShowId())
                        .orElseThrow(
                                () -> new NotFoundException("공연을 찾을 수 없습니다. id=" + performanceId));
        final Long venueId = show.getVenueId();

        final VenueSummary venue =
                venueId == null ? null : venueLookup.findSummary(venueId).orElse(null);

        final Map<Long, PerformanceVenueLayout.SeatLayout> seatLayoutBySeatId =
                venueId == null
                        ? Map.of()
                        : venueSeatLookup.findAllSeatLayouts(venueId).stream()
                                .collect(
                                        Collectors.toMap(
                                                VenueSeatLayout::seatId, this::toSeatLayout));

        final Map<Long, PerformanceVenueLayout.GradeLayout> gradeLayoutByPerformanceGradeId =
                toGradeLayouts(performanceId);

        return new PerformanceVenueLayout(
                performanceId,
                venueId,
                venue == null ? null : venue.name(),
                venue == null ? 0 : venue.seatMapLayout().viewBoxWidth(),
                venue == null ? 0 : venue.seatMapLayout().viewBoxHeight(),
                venue == null ? 0.0 : venue.seatMapLayout().seatDiameter(),
                seatLayoutBySeatId,
                gradeLayoutByPerformanceGradeId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Long> findRepresentativePerformanceId(final long showId) {
        return performanceRepository.findRepresentativePerformanceIdByShowId(showId);
    }

    /** 등급 이름을 찾지 못한 편성은 제외한다 — 옛 {@code join grade}가 그랬듯 조용히 빠진다. */
    private Map<Long, PerformanceVenueLayout.GradeLayout> toGradeLayouts(final long performanceId) {
        final List<PerformanceGrade> performanceGrades =
                performanceRepository.findPerformanceGrades(performanceId);
        final Map<Long, Grade> gradesById =
                gradeRepository.findGradeNames(
                        performanceGrades.stream()
                                .map(PerformanceGrade::getGradeId)
                                .collect(Collectors.toSet()));

        return performanceGrades.stream()
                .filter(performanceGrade -> gradesById.containsKey(performanceGrade.getGradeId()))
                .collect(
                        Collectors.toMap(
                                PerformanceGrade::getId,
                                performanceGrade ->
                                        toGradeLayout(
                                                performanceGrade,
                                                Objects.requireNonNull(
                                                        gradesById.get(
                                                                performanceGrade.getGradeId())))));
    }

    private PerformanceVenueLayout.GradeLayout toGradeLayout(
            final PerformanceGrade performanceGrade, final Grade grade) {
        return new PerformanceVenueLayout.GradeLayout(
                performanceGrade.getId(),
                grade.getCode(),
                grade.getName(),
                performanceGrade.getSortOrder());
    }

    private PerformanceVenueLayout.SeatLayout toSeatLayout(final VenueSeatLayout layout) {
        return new PerformanceVenueLayout.SeatLayout(
                layout.seatId(),
                layout.floor(),
                layout.section(),
                layout.rowNo(),
                layout.seatNo(),
                layout.x(),
                layout.y());
    }
}
