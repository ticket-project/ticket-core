package com.ticket.show;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * booking이 판매 좌석 편성과 주문 표시 snapshot 작성에 쓰는 회차 판매 정보의 불변 snapshot이다.
 *
 * <p>{@code seatInfoBySeatId}는 호출자가 넘긴 seat ID 중 이 회차의 Venue에 실제로 속한 좌석만
 * 채워진다. {@code gradeInfoByPerformanceGradeId}는 이 회차에 배정된 모든 PerformanceGrade를
 * 담는다. show JPA entity를 노출하지 않는다.
 */
public record PerformanceSaleSnapshot(
        long performanceId,
        long showId,
        String showTitle,
        Long venueId,
        String venueName,
        LocalDateTime performanceStartTime,
        Map<Long, SeatInfo> seatInfoBySeatId,
        Map<Long, GradeInfo> gradeInfoByPerformanceGradeId
) {
    public PerformanceSaleSnapshot {
        seatInfoBySeatId = Map.copyOf(seatInfoBySeatId);
        gradeInfoByPerformanceGradeId = Map.copyOf(gradeInfoByPerformanceGradeId);
    }

    /**
     * 물리 좌석 하나의 표시값이다. 회차와 무관하게 고정된 위치 정보만 담는다.
     */
    public record SeatInfo(long seatId, int floor, String section, String rowNo, String seatNo) {

        public String label() {
            return floor + "F " + section + "구역 " + rowNo + "열 " + seatNo + "번";
        }
    }

    /**
     * 회차 하나에 배정된 PerformanceGrade 하나의 표시값과 가격이다.
     */
    public record GradeInfo(
            long performanceGradeId,
            String gradeCode,
            String gradeName,
            int sortOrder,
            BigDecimal price
    ) {
    }
}
