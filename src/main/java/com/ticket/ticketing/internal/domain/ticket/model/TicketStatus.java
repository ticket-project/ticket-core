package com.ticket.ticketing.internal.domain.ticket.model;

/**
 * Ticket(입장 권리)의 상태다. 양도 모델은 존재하지 않는다 — {@code ownerMemberId}는 최초 발급 시
 * 주문 구매자로 고정된다(CONTEXT.md의 Ticket).
 *
 * <p>허용 전이:
 *
 * <pre>
 * ISSUED -> USED
 * ISSUED -> CANCELED
 * </pre>
 */
public enum TicketStatus {
    ISSUED,
    USED,
    CANCELED
}
