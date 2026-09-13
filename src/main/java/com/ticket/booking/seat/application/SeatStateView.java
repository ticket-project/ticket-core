package com.ticket.booking.seat.application;

/**
 * 좌석 상태 API 응답의 좌석 한 건이다. 기존 프론트가 배치도와 상태를 연결하는 물리 {@code seatId}와 회차 판매 좌석 식별자인 {@code
 * performanceSeatId}를 함께 제공한다.
 */
public record SeatStateView(Long performanceSeatId, Long seatId, SeatStatus status) {}
