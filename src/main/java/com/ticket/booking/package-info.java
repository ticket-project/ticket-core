/**
 * Booking module: PerformanceSeat 판매 상태, Selection, Hold, Order/OrderSeat, 주문 취소·만료,
 * 좌석 분산락, Redis Selection/Hold, 좌석 상태 WebSocket 발행, 그리고 확정된 OrderSeat에 발급되는
 * Ticket(entity-only, 원래 별도 ticketing module이었다), 그리고 ticket-queue가 발급한 admission token
 * 검증(원래 별도 admission module이었다)을 소유한다.
 *
 * <p>{@code booking.domain.performancepolicy.model.PerformanceSalesPolicy}가 회차의 예매 접수
 * 기간·Hold 한도·대기열 진입 정책 데이터와 그 모든 판정을 소유한다(ADR 0006 "Performance의 책임
 * 혼재" A2, 원래 show가 갖던 {@code BookingPolicyLookup}/{@code BookingPolicySnapshot}을 이관받았다).
 * {@code performanceId}는 show가 소유한 {@code Performance}에 대한 scalar 식별자일 뿐 cross-module
 * JPA 연관관계나 DB FK가 아니다. 인증 없이 회차의 예매 방식을 조회하는
 * {@code GET /api/v1/booking/performances/{performanceId}/booking-mode}(안내용, 실제 좌석 선택·상태·주문
 * API는 실행 시점에 정책을 다시 검사한다)도 이 module이 공개한다.
 *
 * <p>구현은 하위 package(web/application/domain/infrastructure)에 있다. {@link com.ticket.booking.OrderStarted}/{@link com.ticket.booking.OrderTerminated}는
 * commit 이후 후속 처리(Redis hold 해제, WebSocket 발행)를 위한 공개 이벤트다.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Booking", allowedDependencies = {"show", "member"})
package com.ticket.booking;
