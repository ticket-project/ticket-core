package com.ticket.booking.seat.application;

/**
 * 좌석 상태 조회의 DB 원본 행이다. {@code seatId}는 Redis selection/hold 점유 집합(물리 좌석 기준)과
 * 병합할 때만 쓰는 내부 join key이고, API 응답으로는 {@code performanceSeatId}만 노출한다
 * ({@link SeatStateView} 참고).
 */
public record SeatStateSnapshotRow(
        Long performanceSeatId,
        Long seatId,
        SeatStatus status
) {
}
