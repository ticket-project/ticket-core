package com.ticket.venue.api;

/**
 * venue 안의 물리 좌석 하나(회차와 무관)다. 주소({@code floor}~{@code seatNo})와 배치 좌표({@code x}, {@code y})를 함께 담는다 — 주소만 쓰는 소비자도 있지만
 * 좌표 두 값을 더 받는 비용이 타입을 둘로 나누는 값보다 싸다.
 */
public record VenueSeatSnapshot(
        long seatId, int floor, String section, String rowNo, String seatNo, double x, double y) {}
