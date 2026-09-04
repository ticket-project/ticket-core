package com.ticket.catalog;

/**
 * booking이 회차 정적 seat-map을 조합하는 데 필요한 catalog Venue 배치·물리 Seat 좌표·
 * PerformanceGrade 표시값을 한 번에 조회하는 공개 계약이다.
 */
public interface PerformanceVenueLayoutCatalog {

    /**
     * 존재하지 않는 회차 ID는 공통 오류({@code com.ticket.error.NotFoundException})로 알린다. venue가
     * 없는 show는 {@link PerformanceVenueLayout#seatLayoutBySeatId()}가 빈 맵이다.
     */
    PerformanceVenueLayout getVenueLayout(long performanceId);
}
