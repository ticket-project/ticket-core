package com.ticket.show.application;

import com.ticket.show.domain.PerformanceVenueLayoutContext;

import java.util.List;
import java.util.Optional;

/**
 * {@link com.ticket.show.PerformanceVenueLayoutCatalog}이 조회하는 회차 정적 seat-map 표시값
 * 포트다.
 */
public interface PerformanceVenueLayoutReadRepository {

    Optional<PerformanceVenueLayoutContext> findVenueLayoutContext(long performanceId);

    /**
     * venue에 속한 모든 물리 Seat의 배치 좌표를 반환한다. 이 회차에 실제로 판매 편성됐는지는 여기서
     * 걸러내지 않는다 — booking이 자신의 PerformanceSeat 존재 여부로 거른다.
     */
    List<SeatLayoutRow> findAllSeatLayouts(long venueId);

    /**
     * 이 회차에 배정된 모든 PerformanceGrade의 표시값을 반환한다. 가격은 담지 않는다.
     */
    List<PerformanceGradeLayoutRow> findGradeLayouts(long performanceId);

    record SeatLayoutRow(
            Long seatId,
            int floor,
            String section,
            String rowNo,
            String seatNo,
            double x,
            double y
    ) {
    }

    record PerformanceGradeLayoutRow(
            Long performanceGradeId,
            String gradeCode,
            String gradeName,
            Integer sortOrder
    ) {
    }
}
