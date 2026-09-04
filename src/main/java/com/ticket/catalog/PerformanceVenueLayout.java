package com.ticket.catalog;

import java.util.Map;

/**
 * booking이 회차 정적 seat-map 응답(Venue 배치·물리 Seat 좌표·PerformanceGrade 표시값)을 조합하는 데
 * 쓰는 불변 snapshot이다.
 *
 * <p>{@code seatLayoutBySeatId}는 이 회차의 Venue에 속한 모든 물리 Seat를 담는다 — 실제로 이 회차에
 * 판매 편성(PerformanceSeat)됐는지는 이 snapshot의 관심사가 아니고, 호출하는 booking 쪽이
 * PerformanceSeat 존재 여부로 걸러낸다. {@code gradeLayoutByPerformanceGradeId}는 이 회차에 배정된
 * 모든 PerformanceGrade를 담는다. catalog JPA entity를 노출하지 않는다.
 */
public record PerformanceVenueLayout(
        long performanceId,
        Long venueId,
        String venueName,
        int viewBoxWidth,
        int viewBoxHeight,
        double seatDiameter,
        Map<Long, SeatLayout> seatLayoutBySeatId,
        Map<Long, GradeLayout> gradeLayoutByPerformanceGradeId
) {
    public PerformanceVenueLayout {
        seatLayoutBySeatId = Map.copyOf(seatLayoutBySeatId);
        gradeLayoutByPerformanceGradeId = Map.copyOf(gradeLayoutByPerformanceGradeId);
    }

    /**
     * 물리 좌석 하나의 배치 좌표다. 회차와 무관하게 고정된 위치 정보만 담는다.
     */
    public record SeatLayout(
            long seatId,
            int floor,
            String section,
            String rowNo,
            String seatNo,
            double x,
            double y
    ) {
    }

    /**
     * 회차 하나에 배정된 PerformanceGrade 하나의 표시값이다. 가격은 담지 않는다 — seat-map 응답의
     * 좌석별 가격은 판매 시점에 확정돼 불변인 booking {@code PerformanceSeat.unitPrice}가 원본이다.
     */
    public record GradeLayout(
            long performanceGradeId,
            String gradeCode,
            String gradeName,
            int sortOrder
    ) {
    }
}
