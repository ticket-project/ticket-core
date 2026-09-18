package com.ticket.booking.seat.query;

/**
 * 좌석 상태 조회의 DB 원본 행이자 API 응답 한 건이다 — 조회 결과에 Redis 점유만 덧씌우면 그대로 응답이 되므로 별도 타입으로 나누지 않는다.
 *
 * <p>기존 프론트가 배치도와 상태를 연결하는 물리 {@code seatId}와 회차 판매 좌석 식별자인 {@code performanceSeatId}를 함께 제공한다.
 * {@code seatId}는 Redis selection/hold 점유 집합(물리 좌석 기준)과 병합할 join key이기도 하다.
 */
public record SeatStateView(Long performanceSeatId, Long seatId, SeatStatus status) {}
