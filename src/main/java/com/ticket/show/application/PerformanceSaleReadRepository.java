package com.ticket.show.application;

import com.ticket.show.domain.PerformanceSaleContext;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * {@link com.ticket.show.PerformanceSaleCatalog}이 조회하는 회차 판매 표시값 포트다.
 */
public interface PerformanceSaleReadRepository {

    Optional<PerformanceSaleContext> findContext(long performanceId);

    /**
     * venue에 속한 좌석 중 요청한 seatId만 반환한다. 빈 {@code seatIds}는 빈 목록을 반환한다.
     */
    List<SeatAddressRow> findSeatAddresses(long venueId, Collection<Long> seatIds);

    /**
     * 이 회차에 배정된 모든 PerformanceGrade를 반환한다.
     */
    List<PerformanceGradeRow> findPerformanceGrades(long performanceId);

    record SeatAddressRow(Long seatId, int floor, String section, String rowNo, String seatNo) {
    }

    record PerformanceGradeRow(
            Long performanceGradeId,
            String gradeCode,
            String gradeName,
            Integer sortOrder,
            BigDecimal price
    ) {
    }
}
