/**
 * Booking module: PerformanceSeat 판매 상태, Selection, Hold, Order/OrderSeat, 주문 취소·만료,
 * 좌석 분산락, Redis Selection/Hold, 좌석 상태 WebSocket 발행을 소유한다.
 *
 * <p>실제 코드는 아직 이 module로 이동하지 않았다 — 이 package는 target module 경계만 먼저 선언한
 * 빈 skeleton이다.
 */
@ApplicationModule(displayName = "Booking", allowedDependencies = {"catalog", "identity", "admission"})
package com.ticket.booking;

import org.springframework.modulith.ApplicationModule;
