package com.ticket.payment.internal.domain.payment.model;

import com.ticket.payment.internal.domain.PaymentAuditedEntity;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Order에 대한 한 번의 결제 시도다(CONTEXT.md의 Payment, ADR 0005).
 *
 * <p>Order 하나에는 여러 Payment가 있을 수 있고(`1:0..N`), Payment 한 건이 실패해도 Order는 만료
 * 전까지 다시 결제를 시도할 수 있다. {@code orderId}는 booking {@code Order}에 대한 scalar 참조일
 * 뿐 JPA 연관관계가 아니다 — cross-module JPA 관계와 물리 FK는 만들지 않는다(ADR 0003, ADR 0005 §4).
 *
 * <p>이번 entity-only 단계는 PG client, 승인/실패/취소 API, callback/webhook을 구현하지 않는다.
 * 아래 상태 전이 메서드는 그 자체가 PG 연동이 아니라, Payment aggregate가 자신의 상태 불변식을
 * 스스로 지키게 하는 도메인 규칙이다(추후 후속 작업이 이 메서드를 호출한다).
 */
@Getter
@Entity
@Table(
        name = "PAYMENTS",
        uniqueConstraints = {
                @UniqueConstraint(name = "UK_PAYMENTS_PAYMENT_KEY", columnNames = "payment_key"),
                @UniqueConstraint(name = "UK_PAYMENTS_ORDER_ATTEMPT", columnNames = {"order_id", "attempt_no"}),
                @UniqueConstraint(name = "UK_PAYMENTS_PROVIDER_PAYMENT_KEY", columnNames = "provider_payment_key")
        },
        indexes = {
                @Index(name = "IDX_PAYMENTS_ORDER_STATUS", columnList = "order_id,status")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment extends PaymentAuditedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "payment_key", nullable = false, length = 64)
    private String paymentKey;

    @Column(name = "attempt_no", nullable = false)
    private Integer attemptNo;

    @Column(nullable = false, length = 32)
    private String provider;

    @Column(nullable = false, length = 32)
    private String method;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PaymentStatus status;

    @Column(name = "provider_payment_key", length = 64)
    private String providerPaymentKey;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "failed_at")
    private LocalDateTime failedAt;

    @Column(name = "canceled_at")
    private LocalDateTime canceledAt;

    @Column(name = "failure_code", length = 64)
    private String failureCode;

    @Column(name = "failure_message", length = 1000)
    private String failureMessage;

    private Payment(
            final Long orderId,
            final String paymentKey,
            final Integer attemptNo,
            final String provider,
            final String method,
            final BigDecimal amount,
            final LocalDateTime requestedAt
    ) {
        this.orderId = orderId;
        this.paymentKey = paymentKey;
        this.attemptNo = attemptNo;
        this.provider = provider;
        this.method = method;
        this.amount = amount;
        this.status = PaymentStatus.READY;
        this.requestedAt = requestedAt;
    }

    public static Payment request(
            final Long orderId,
            final String paymentKey,
            final Integer attemptNo,
            final String provider,
            final String method,
            final BigDecimal amount,
            final LocalDateTime requestedAt
    ) {
        return new Payment(orderId, paymentKey, attemptNo, provider, method, amount, requestedAt);
    }

    public void process() {
        if (status != PaymentStatus.READY) {
            throw new IllegalStateException("READY 상태의 Payment만 process 할 수 있습니다. currentStatus=" + status);
        }
        this.status = PaymentStatus.PROCESSING;
    }

    public void approve(final String providerPaymentKey, final LocalDateTime approvedAt) {
        validateRetryableTransition("approve");
        this.status = PaymentStatus.SUCCEEDED;
        this.providerPaymentKey = providerPaymentKey;
        this.approvedAt = approvedAt;
    }

    public void fail(final String failureCode, final String failureMessage, final LocalDateTime failedAt) {
        validateRetryableTransition("fail");
        this.status = PaymentStatus.FAILED;
        this.failureCode = failureCode;
        this.failureMessage = failureMessage;
        this.failedAt = failedAt;
    }

    public void cancel(final LocalDateTime canceledAt) {
        validateRetryableTransition("cancel");
        this.status = PaymentStatus.CANCELED;
        this.canceledAt = canceledAt;
    }

    public boolean isTerminal() {
        return status == PaymentStatus.SUCCEEDED || status == PaymentStatus.FAILED || status == PaymentStatus.CANCELED;
    }

    private void validateRetryableTransition(final String action) {
        if (status != PaymentStatus.READY && status != PaymentStatus.PROCESSING) {
            throw new IllegalStateException(
                    "READY/PROCESSING 상태의 Payment만 " + action + " 할 수 있습니다. currentStatus=" + status);
        }
    }
}
