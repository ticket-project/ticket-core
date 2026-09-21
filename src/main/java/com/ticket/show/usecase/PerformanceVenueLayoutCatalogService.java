package com.ticket.show.usecase;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ticket.shared.exception.NotFoundException;
import com.ticket.show.api.PerformanceVenueLayout;
import com.ticket.show.api.PerformanceVenueLayoutCatalogApi;
import com.ticket.show.domain.performance.PerformanceRepository;
import com.ticket.show.domain.performance.PerformanceVenueLayoutContext;
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
    private final VenueLookupApi venueLookup;
    private final VenueSeatLookupApi venueSeatLookup;

    @Override
    @Transactional(readOnly = true)
    public PerformanceVenueLayout getVenueLayout(final long performanceId) {
        final PerformanceVenueLayoutContext context =
                performanceRepository
                        .findVenueLayoutContext(performanceId)
                        .orElseThrow(
                                () -> new NotFoundException("공연을 찾을 수 없습니다. id=" + performanceId));

        final VenueSummary venue =
                context.venueId() == null
                        ? null
                        : venueLookup.findSummary(context.venueId()).orElse(null);

        final Map<Long, PerformanceVenueLayout.SeatLayout> seatLayoutBySeatId =
                context.venueId() == null
                        ? Map.of()
                        : venueSeatLookup.findAllSeatLayouts(context.venueId()).stream()
                                .collect(
                                        Collectors.toMap(
                                                VenueSeatLayout::seatId, this::toSeatLayout));

        final Map<Long, PerformanceVenueLayout.GradeLayout> gradeLayoutByPerformanceGradeId =
                performanceRepository.findGradeLayouts(performanceId).stream()
                        .collect(
                                Collectors.toMap(
                                        PerformanceVenueLayout.GradeLayout::performanceGradeId,
                                        gradeLayout -> gradeLayout));

        return new PerformanceVenueLayout(
                performanceId,
                context.venueId(),
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
