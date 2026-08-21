package com.ticket.core.domain.payment.model;

import com.ticket.core.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Entity
@Table(
        name = "PAYMENTS",
        indexes = {
                @Index(name = "IDX_PAYMENTS_ORDER_ID", columnList = "order_id")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment extends BaseEntity {

    private static final int MAX_FAILURE_MESSAGE_LENGTH = 200;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 40)
    private String paymentKey;

    @Column(nullable = false)
    private Long orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentMethod method;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @Column(length = 64)
    private String pgTransactionId;

    private LocalDateTime approvedAt;

    private LocalDateTime failedAt;

    @Column(length = 40)
    private String failureCode;

    @Column(length = MAX_FAILURE_MESSAGE_LENGTH)
    private String failureMessage;

    public Payment(
            final String paymentKey,
            final Long orderId,
            final PaymentMethod method,
            final BigDecimal amount
    ) {
        this.paymentKey = paymentKey;
        this.orderId = orderId;
        this.method = method;
        this.amount = amount;
        this.status = PaymentStatus.READY;
    }

    public void approve(final LocalDateTime now, final String pgTransactionId) {
        validateReadyTransition("approve");
        this.status = PaymentStatus.APPROVED;
        this.approvedAt = now;
        this.pgTransactionId = pgTransactionId;
    }

    public void fail(final LocalDateTime now, final String failureCode, final String failureMessage) {
        validateReadyTransition("fail");
        this.status = PaymentStatus.FAILED;
        this.failedAt = now;
        this.failureCode = failureCode;
        this.failureMessage = truncate(failureMessage);
    }

    public boolean isReady() {
        return status == PaymentStatus.READY;
    }

    public boolean isApproved() {
        return status == PaymentStatus.APPROVED;
    }

    private void validateReadyTransition(final String action) {
        if (!isReady()) {
            throw new IllegalStateException("READY 결제만 " + action + " 할 수 있습니다. currentStatus=" + status);
        }
    }

    private static String truncate(final String failureMessage) {
        if (failureMessage == null || failureMessage.length() <= MAX_FAILURE_MESSAGE_LENGTH) {
            return failureMessage;
        }
        return failureMessage.substring(0, MAX_FAILURE_MESSAGE_LENGTH);
    }
}
