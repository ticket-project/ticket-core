package com.ticket.booking.domain.order.model;

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
public class Order extends BookingAuditedEntity {

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

    /**
     * 주문 생성 시점의 표시 snapshot이다(ADR 0005). show의 Show/Performance/Venue 표시값이 나중에
     * 바뀌어도 이미 만든 주문 상세는 바뀌지 않아야 하므로 다시 조회하지 않고 이 값을 그대로 쓴다.
     */
    @Column(name = "show_title_snapshot", nullable = false, length = 1000)
    private String showTitleSnapshot;

    @Column(name = "performance_start_at_snapshot", nullable = false)
    private LocalDateTime performanceStartAtSnapshot;

    @Column(name = "venue_name_snapshot", nullable = false)
    private String venueNameSnapshot;

    private LocalDateTime confirmedAt;

    private LocalDateTime expiredAt;

    private LocalDateTime canceledAt;

    public Order(
            final Long memberId,
            final Long performanceId,
            final String orderKey,
            final String holdKey,
            final BigDecimal totalAmount,
            final LocalDateTime expiresAt,
            final String showTitleSnapshot,
            final LocalDateTime performanceStartAtSnapshot,
            final String venueNameSnapshot
    ) {
        this.memberId = memberId;
        this.performanceId = performanceId;
        this.orderKey = orderKey;
        this.holdKey = holdKey;
        this.status = OrderState.PENDING;
        this.totalAmount = totalAmount;
        this.expiresAt = expiresAt;
        this.showTitleSnapshot = showTitleSnapshot;
        this.performanceStartAtSnapshot = performanceStartAtSnapshot;
        this.venueNameSnapshot = venueNameSnapshot;
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
