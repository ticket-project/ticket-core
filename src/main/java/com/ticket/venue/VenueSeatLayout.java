package com.ticket.venue;

/**
 * venue 안의 물리 좌석 배치 좌표(회차와 무관)다.
 */
public record VenueSeatLayout(long seatId, int floor, String section, String rowNo, String seatNo, double x, double y) {
}
