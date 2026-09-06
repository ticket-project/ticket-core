package com.ticket.booking.domain.ticket.model;

import com.ticket.booking.domain.BookingAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 결제 성공으로 확정된 OrderSeat에 대해 발급되는 입장 권리다(CONTEXT.md의 Ticket, ADR 0005).
 *
 * <p>OrderSeat는 결제 전에는 Ticket이 없고, 발급 후에는 최대 하나만 가진다(`1:0..1`). {@code
 * ownerMemberId}는 member {@code Member}에 대한 scalar 참조일 뿐 JPA 연관관계가 아니다 — cross-module
 * JPA 관계와 물리 FK는 만들지 않는다(ADR 0003). {@code orderSeatId}는 같은 booking module의
 * {@code OrderSeat}를 가리키지만 기존 schema 관례대로 scalar 컬럼으로 둔다. Ticket은 원래 별도
 * {@code ticketing} module(ADR 0005)이었으나 booking으로 흡수됐다. {@code ownerMemberId}는 최초 발급 시 주문 구매자로 고정되고, 양도 모델은
 * 존재하지 않는다.
 *
 * <p>이번 entity-only 단계는 {@code OrderConfirmed} listener, 자동 발급, QR, 입장, 사용, 취소,
 * 환불, 양도 API를 구현하지 않는다. 아래 상태 전이 메서드는 그 자체가 그 흐름의 구현이 아니라,
 * Ticket aggregate가 자신의 상태 불변식을 스스로 지키게 하는 도메인 규칙이다(추후 후속 작업이 이
 * 메서드를 호출한다).
 */
@Getter
@Entity
@Table(
        name = "TICKETS",
        uniqueConstraints = {
                @UniqueConstraint(name = "UK_TICKETS_TICKET_KEY", columnNames = "ticket_key"),
                @UniqueConstraint(name = "UK_TICKETS_ORDER_SEAT_ID", columnNames = "order_seat_id")
        },
        indexes = {
                @Index(name = "IDX_TICKETS_OWNER_MEMBER_STATUS", columnList = "owner_member_id,status")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Ticket extends BookingAuditedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticket_key", nullable = false, length = 64)
    private String ticketKey;

    @Column(name = "order_seat_id", nullable = false)
    private Long orderSeatId;

    @Column(name = "owner_member_id", nullable = false)
    private Long ownerMemberId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TicketStatus status;

    @Column(name = "issued_at", nullable = false)
    private LocalDateTime issuedAt;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    @Column(name = "canceled_at")
    private LocalDateTime canceledAt;

    private Ticket(
            final String ticketKey,
            final Long orderSeatId,
            final Long ownerMemberId,
            final LocalDateTime issuedAt
    ) {
        this.ticketKey = ticketKey;
        this.orderSeatId = orderSeatId;
        this.ownerMemberId = ownerMemberId;
        this.status = TicketStatus.ISSUED;
        this.issuedAt = issuedAt;
    }

    public static Ticket issue(
            final String ticketKey,
            final Long orderSeatId,
            final Long ownerMemberId,
            final LocalDateTime issuedAt
    ) {
        return new Ticket(ticketKey, orderSeatId, ownerMemberId, issuedAt);
    }

    public void use(final LocalDateTime usedAt) {
        validateIssued("use");
        this.status = TicketStatus.USED;
        this.usedAt = usedAt;
    }

    public void cancel(final LocalDateTime canceledAt) {
        validateIssued("cancel");
        this.status = TicketStatus.CANCELED;
        this.canceledAt = canceledAt;
    }

    public boolean isTerminal() {
        return status == TicketStatus.USED || status == TicketStatus.CANCELED;
    }

    private void validateIssued(final String action) {
        if (status != TicketStatus.ISSUED) {
            throw new IllegalStateException(
                    "ISSUED 상태의 Ticket만 " + action + " 할 수 있습니다. currentStatus=" + status);
        }
    }
}
