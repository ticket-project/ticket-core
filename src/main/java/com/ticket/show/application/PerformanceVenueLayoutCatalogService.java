package com.ticket.show.application;

import com.ticket.show.PerformanceVenueLayout;
import com.ticket.show.PerformanceVenueLayoutCatalog;
import com.ticket.show.application.PerformanceVenueLayoutReadRepository;
import com.ticket.show.application.PerformanceVenueLayoutReadRepository.PerformanceGradeLayoutRow;
import com.ticket.show.application.PerformanceVenueLayoutReadRepository.SeatLayoutRow;
import com.ticket.show.domain.PerformanceVenueLayoutContext;
import com.ticket.error.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * {@link PerformanceVenueLayoutCatalog}의 show 소유 구현이다. 회차 정적 seat-map에 필요한
 * venue·좌석 좌표·등급 표시값을 한 번에 조회해 booking에게 scalar snapshot만 넘긴다.
 */
@Service
@RequiredArgsConstructor
public class PerformanceVenueLayoutCatalogService implements PerformanceVenueLayoutCatalog {

    private final PerformanceVenueLayoutReadRepository performanceVenueLayoutReadRepository;

    @Override
    @Transactional(readOnly = true)
    public PerformanceVenueLayout getVenueLayout(final long performanceId) {
        final PerformanceVenueLayoutContext context = performanceVenueLayoutReadRepository
                .findVenueLayoutContext(performanceId)
                .orElseThrow(() -> new NotFoundException("공연을 찾을 수 없습니다. id=" + performanceId));

        final Map<Long, PerformanceVenueLayout.SeatLayout> seatLayoutBySeatId = context.venueId() == null
                ? Map.of()
                : performanceVenueLayoutReadRepository.findAllSeatLayouts(context.venueId()).stream()
                        .collect(Collectors.toMap(SeatLayoutRow::seatId, this::toSeatLayout));

        final Map<Long, PerformanceVenueLayout.GradeLayout> gradeLayoutByPerformanceGradeId =
                performanceVenueLayoutReadRepository.findGradeLayouts(performanceId).stream()
                        .collect(Collectors.toMap(PerformanceGradeLayoutRow::performanceGradeId, this::toGradeLayout));

        return new PerformanceVenueLayout(
                performanceId,
                context.venueId(),
                context.venueName(),
                context.viewBoxWidth() == null ? 0 : context.viewBoxWidth(),
                context.viewBoxHeight() == null ? 0 : context.viewBoxHeight(),
                context.seatDiameter() == null ? 0.0 : context.seatDiameter(),
                seatLayoutBySeatId,
                gradeLayoutByPerformanceGradeId
        );
    }

    private PerformanceVenueLayout.SeatLayout toSeatLayout(final SeatLayoutRow row) {
        return new PerformanceVenueLayout.SeatLayout(
                row.seatId(), row.floor(), row.section(), row.rowNo(), row.seatNo(), row.x(), row.y()
        );
    }

    private PerformanceVenueLayout.GradeLayout toGradeLayout(final PerformanceGradeLayoutRow row) {
        return new PerformanceVenueLayout.GradeLayout(
                row.performanceGradeId(), row.gradeCode(), row.gradeName(), row.sortOrder()
        );
    }
}
