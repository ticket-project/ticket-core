/**
 * Booking BC: PerformanceSeat 판매 상태, Selection, Hold, Order/OrderSeat, 주문 취소·만료, 좌석
 * 분산락, Redis Selection/Hold, 좌석 상태 WebSocket 발행, 확정된 OrderSeat에 발급되는 Ticket,
 * 그리고 ticket-queue가 발급한 admission token 검증을 소유한다.
 *
 * <p>{@code booking.salespolicy.domain.PerformanceSalesPolicy}가 회차의 예매 접수
 * 기간·Hold 한도·대기열 진입 정책 데이터와 그 모든 판정을 소유한다(ADR 0006 "Performance의 책임
 * 혼재" A2). {@code performanceId}는 show가 소유한 {@code Performance}에 대한 scalar 식별자일 뿐
 * cross-module JPA 연관관계나 DB FK가 아니다. 인증 없이 회차의 예매 방식을 조회하는
 * {@code GET /api/v1/booking/performances/{performanceId}/booking-mode}도 이 module이 공개한다.
 *
 * 공개 계약:
 * - OrderStarted / OrderTerminated (commit 이후 후속 처리를 위한 이벤트)
 */
@org.springframework.modulith.ApplicationModule(displayName = "Booking", allowedDependencies = {"show", "member"})
package com.ticket.booking;

