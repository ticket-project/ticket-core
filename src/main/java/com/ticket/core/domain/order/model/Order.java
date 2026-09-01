package com.ticket.core.domain.order.model;

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
        name = "ORDERS",
        indexes = {
                @Index(name = "IDX_ORDERS_MEMBER_PERFORMANCE_STATUS", columnList = "member_id,performance_id,status")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long memberId;

    @Column(nullable = false)
    private Long performanceId;

    @Column(nullable = false, unique = true, length = 40)
    private String orderKey;

    @Column(nullable = false, unique = true, length = 64)
    private String holdKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private OrderState status;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal totalAmount;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    private LocalDateTime confirmedAt;

    private LocalDateTime expiredAt;

    private LocalDateTime canceledAt;

    private LocalDateTime paymentFailedAt;

    public Order(
            final Long memberId,
            final Long performanceId,
            final String orderKey,
            final String holdKey,
            final BigDecimal totalAmount,
            final LocalDateTime expiresAt
    ) {
        this.memberId = memberId;
        this.performanceId = performanceId;
        this.orderKey = orderKey;
        this.holdKey = holdKey;
        this.status = OrderState.PENDING;
        this.totalAmount = totalAmount;
        this.expiresAt = expiresAt;
    }

    public void confirm(final LocalDateTime now) {
        validatePendingTransition("confirm");
        this.status = OrderState.CONFIRMED;
        this.confirmedAt = now;
    }

    public void expire(final LocalDateTime now) {
        validatePendingTransition("expire");
        this.status = OrderState.EXPIRED;
        this.expiredAt = now;
    }

    public void cancel(final LocalDateTime now) {
        validatePendingTransition("cancel");
        this.status = OrderState.CANCELED;
        this.canceledAt = now;
    }

    public void failPayment(final LocalDateTime now) {
        validatePendingTransition("failPayment");
        this.status = OrderState.PAYMENT_FAILED;
        this.paymentFailedAt = now;
    }

    public boolean isPending() {
        return status == OrderState.PENDING;
    }

    public boolean isExpired(final LocalDateTime now) {
        return isPending() && (expiresAt.isBefore(now) || expiresAt.isEqual(now));
    }

    private void validatePendingTransition(final String action) {
        if (!isPending()) {
            throw new IllegalStateException("PENDING 주문만 " + action + " 할 수 있습니다. currentStatus=" + status);
        }
    }
}
