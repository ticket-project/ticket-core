package com.ticket.venue;

/**
 * venue 안의 물리 좌석 주소(회차와 무관)다.
 */
public record VenueSeatAddress(long seatId, int floor, String section, String rowNo, String seatNo) {
}
