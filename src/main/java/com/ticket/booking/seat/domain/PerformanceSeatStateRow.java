package com.ticket.booking.seat.domain;

/**
 * 좌석 상태 조회가 읽는 회차 좌석 한 행의 세 열이다.
 *
 * <p>엔티티 대신 이 타입으로 읽는 이유는 크기다. 15,000석 회차에서 엔티티로 읽으면 11개 열을 받아 엔티티 15,000개로 바꾸는 동안 DB 연결을 쥔다. 부하 측정에서 DB 실행은 8.8ms인데
 * Core 쪽 호출은 36~42ms였고, 그 차이가 연결 풀을 채우는 가장 큰 몫이었다(gatling-test capacity-log P-008).
 */
public record PerformanceSeatStateRow(Long performanceSeatId, Long seatId, PerformanceSeatState state) {}
