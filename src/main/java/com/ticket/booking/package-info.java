/**
 * Booking BC: PerformanceSeat 판매 상태, Selection, Hold, Order/OrderSeat, 주문 취소·만료, 좌석 분산락, Redis Selection/Hold, 좌석 상태
 * WebSocket 발행, 확정된 OrderSeat에 발급되는 Ticket, 그리고 ticket-queue가 발급한 admission token 검증을 소유한다.
 *
 * <p>{@code booking.domain.salespolicy.PerformanceSalesPolicy}가 회차의 예매 접수 기간·Hold 한도·대기열 진입 정책 데이터와 그 모든 판정을 소유한다(ADR
 * 0006 "Performance의 책임 혼재" A2). {@code performanceId}는 show가 소유한 {@code Performance}에 대한 scalar 식별자일 뿐 cross-module
 * JPA 연관관계나 DB FK가 아니다. 인증 없이 회차의 예매 방식을 조회하는 {@code GET /api/v1/booking/performances/{performanceId}/booking-mode}도 이
 * module이 공개한다.
 *
 * <p>공개 계약: {@link com.ticket.booking.OrderStarted} / {@link com.ticket.booking.OrderTerminated} (commit 이후 후속 처리를 위한
 * 이벤트).
 *
 * <p><b>이 둘만 module root에 남는다 — 다른 module처럼 {@code booking.api}로 옮기지 않는다.</b> 두 가지 이유다. (1) 다른 module이 호출하는 표면이 아니다. 두
 * 이벤트의 listener는 {@code booking.event}에 있는 booking 자신이고, 다른 module의 production code는 이 타입을 참조하지 않는다 — 참조가 없는 곳에
 * {@code api} package를 만들지 않는다. (2) 더 중요한 이유로, <b>이 두 FQCN은 DB에 저장된 값이다.</b> Modulith event publication registry의
 * {@code EVENT_PUBLICATION.event_type} 컬럼이 이벤트 class의 FQCN을 그대로 담는다. package를 옮기면 배포 시점에 아직 완료되지 않은 publication row의
 * {@code event_type}이 지금 코드에 없는 class를 가리키게 되어 재처리가 되지 않는다. 같은 성격의 사고를 listener id에서 이미 한 번 겪었고 그 회귀를
 * {@code com.ticket.booking.event.BookingEventListenerIdContractTest}가 고정하고 있다.
 *
 * <p>{@code security} 의존은 WebSocket 인증 하나뿐이다 — STOMP CONNECT는 HTTP filter chain을 타지 않아 좌석 상태 구독 인터셉터가
 * {@code AccessTokenAuthenticationApi}로 토큰을 직접 검증한다.
 *
 * <p><b>하위 package는 Application Module이 아니다.</b> {@code order}·{@code seat}·{@code hold}처럼 업무 단위로 탐색하기 위한 ordinary
 * package일 뿐이라 {@code @ApplicationModule}도 {@code @NamedInterface}도 붙이지 않는다. module 경계와 의존 DAG는 이 package 하나가 그대로 갖는다.
 */
@NullMarked
@org.springframework.modulith.ApplicationModule(
        displayName = "Booking",
        allowedDependencies = {
            "show :: api",
            "member :: api",
            "security :: api",
            "shared :: api",
            "shared :: config",
            "shared :: web",
            "shared :: exception",
            "shared :: jpa"
        })
package com.ticket.booking;

import org.jspecify.annotations.NullMarked;
