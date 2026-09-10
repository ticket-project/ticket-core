package com.ticket.show.performance.application;

import com.ticket.show.performance.application.port.PerformanceVenueLayoutQueryPort;

import com.ticket.show.PerformanceVenueLayout;
import com.ticket.show.PerformanceVenueLayoutCatalog;
import com.ticket.show.performance.application.port.PerformanceVenueLayoutQueryPort.PerformanceGradeLayoutRow;
import com.ticket.show.performance.domain.PerformanceVenueLayoutContext;
import com.ticket.error.NotFoundException;
import com.ticket.venue.VenueLookup;
import com.ticket.venue.VenueSeatLayout;
import com.ticket.venue.VenueSeatLookup;
import com.ticket.venue.VenueSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * {@link PerformanceVenueLayoutCatalog}의 show 소유 구현이다. 회차 정적 seat-map에 필요한
 * venue·좌석 좌표·등급 표시값을 한 번에 조회해 booking에게 scalar snapshot만 넘긴다.
 *
 * <p>venue 조합(venue 이름·seat-map 좌표·좌석 배치)은 이 application 계층이 한다 — persistence
 * adapter({@code PerformanceVenueLayoutQueryPort}의 구현)는 show 자기 DB만 본다.
 */
@Service
@RequiredArgsConstructor
public class PerformanceVenueLayoutCatalogService implements PerformanceVenueLayoutCatalog {

    private final PerformanceVenueLayoutQueryPort performanceVenueLayoutQueryPort;
    private final VenueLookup venueLookup;
    private final VenueSeatLookup venueSeatLookup;

    @Override
    @Transactional(readOnly = true)
    public PerformanceVenueLayout getVenueLayout(final long performanceId) {
        final PerformanceVenueLayoutContext context = performanceVenueLayoutQueryPort
                .findVenueLayoutContext(performanceId)
                .orElseThrow(() -> new NotFoundException("공연을 찾을 수 없습니다. id=" + performanceId));

        final VenueSummary venue = context.venueId() == null
                ? null
                : venueLookup.findSummary(context.venueId()).orElse(null);

        final Map<Long, PerformanceVenueLayout.SeatLayout> seatLayoutBySeatId = context.venueId() == null
                ? Map.of()
                : venueSeatLookup.findAllSeatLayouts(context.venueId()).stream()
                        .collect(Collectors.toMap(VenueSeatLayout::seatId, this::toSeatLayout));

        final Map<Long, PerformanceVenueLayout.GradeLayout> gradeLayoutByPerformanceGradeId =
                performanceVenueLayoutQueryPort.findGradeLayouts(performanceId).stream()
                        .collect(Collectors.toMap(PerformanceGradeLayoutRow::performanceGradeId, this::toGradeLayout));

        return new PerformanceVenueLayout(
                performanceId,
                context.venueId(),
                venue == null ? null : venue.name(),
                venue == null ? 0 : venue.seatMapLayout().viewBoxWidth(),
                venue == null ? 0 : venue.seatMapLayout().viewBoxHeight(),
                venue == null ? 0.0 : venue.seatMapLayout().seatDiameter(),
                seatLayoutBySeatId,
                gradeLayoutByPerformanceGradeId
        );
    }

    private PerformanceVenueLayout.SeatLayout toSeatLayout(final VenueSeatLayout layout) {
        return new PerformanceVenueLayout.SeatLayout(
                layout.seatId(), layout.floor(), layout.section(), layout.rowNo(), layout.seatNo(), layout.x(), layout.y()
        );
    }

    private PerformanceVenueLayout.GradeLayout toGradeLayout(final PerformanceGradeLayoutRow row) {
        return new PerformanceVenueLayout.GradeLayout(
                row.performanceGradeId(), row.gradeCode(), row.gradeName(), row.sortOrder()
        );
    }
}
