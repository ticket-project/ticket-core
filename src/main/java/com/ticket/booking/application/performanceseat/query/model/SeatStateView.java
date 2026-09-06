package com.ticket.booking.application.performanceseat.query.model;

/**
 * 좌석 상태 API 응답의 좌석 한 건이다. 외부 판매 좌석 식별자는 물리 {@code seatId}가 아니라 이 회차의
 * 판매 편성(PerformanceSeat) ID인 {@code performanceSeatId}다.
 */
public record SeatStateView(
        Long performanceSeatId,
        SeatStatus status
) {
}
