/**
 * Booking module: PerformanceSeat 판매 상태, Selection, Hold, Order/OrderSeat, 주문 취소·만료,
 * 좌석 분산락, Redis Selection/Hold, 좌석 상태 WebSocket 발행, 그리고 확정된 OrderSeat에 발급되는
 * Ticket(entity-only, 원래 별도 ticketing module이었다), 그리고 ticket-queue가 발급한 admission token
 * 검증(원래 별도 admission module이었다)을 소유한다.
 *
 * <p>구현은 하위 package(web/application/domain/infrastructure)에 있다. {@link com.ticket.booking.OrderStarted}/{@link com.ticket.booking.OrderTerminated}는
 * commit 이후 후속 처리(Redis hold 해제, WebSocket 발행)를 위한 공개 이벤트다.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Booking", allowedDependencies = {"show", "member"})
package com.ticket.booking;
