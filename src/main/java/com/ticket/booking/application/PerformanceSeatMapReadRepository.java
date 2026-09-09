package com.ticket.booking.application;

import java.math.BigDecimal;
import java.util.List;

/**
 * 회차 정적 seat-map에 필요한 booking local 판매 편성(PerformanceSeat) 조회 포트다. 이 회차에 실제로
 * 판매 편성된 좌석만 반환한다 — 편성되지 않은 물리 Seat는 여기 나타나지 않으므로 seat-map 응답에서도
 * 자연히 제외된다.
 */
public interface PerformanceSeatMapReadRepository {

    List<PerformanceSeatMapRow> findAllByPerformanceId(Long performanceId);

    record PerformanceSeatMapRow(
            Long performanceSeatId,
            Long seatId,
            Long performanceGradeId,
            BigDecimal unitPrice
    ) {
    }
}
