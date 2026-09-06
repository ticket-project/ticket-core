/**
 * Booking module: PerformanceSeat 판매 상태, Selection, Hold, Order/OrderSeat, 주문 취소·만료,
 * 좌석 분산락, Redis Selection/Hold, 좌석 상태 WebSocket 발행을 소유한다.
 *
 * <p>구현은 {@code internal} 아래에 있다. {@link com.ticket.booking.OrderStarted}/{@link com.ticket.booking.OrderTerminated}는
 * commit 이후 후속 처리(Redis hold 해제, WebSocket 발행)를 위한 공개 이벤트다.
 */
@ApplicationModule(displayName = "Booking", allowedDependencies = {"catalog", "member", "admission"})
package com.ticket.booking;

import org.springframework.modulith.ApplicationModule;
