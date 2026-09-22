package com.ticket.booking.order.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import org.jspecify.annotations.Nullable;

import com.ticket.shared.jpa.AuditedEntity;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "ORDERS",
        indexes = {@Index(name = "IDX_ORDERS_MEMBER_PERFORMANCE_STATUS", columnList = "member_id,performance_id,status")
        })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order extends AuditedEntity {
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
     * 주문 생성 시점의 표시 snapshot이다(ADR 0005). show의 Show/Performance/Venue 표시값이 나중에 바뀌어도 이미 만든 주문 상세는 바뀌지 않아야 하므로 다시 조회하지 않고
     * 이 값을 그대로 쓴다.
     */
    @Column(name = "show_title_snapshot", nullable = false, length = 1000)
    private String showTitleSnapshot;

    @Column(name = "performance_start_at_snapshot", nullable = false)
    private LocalDateTime performanceStartAtSnapshot;

    @Column(name = "venue_name_snapshot", nullable = false)
    private String venueNameSnapshot;

    // 종료 시각은 해당 전이가 일어난 뒤에만 채워진다 — PENDING 주문에서는 셋 다 null이다.
    private @Nullable LocalDateTime confirmedAt;
    private @Nullable LocalDateTime expiredAt;
    private @Nullable LocalDateTime canceledAt;

    /**
     * 이 주문이 포함한 좌석이다({@code 1:1..N}, 빈 주문은 없다). Order aggregate 안의 자식이라 {@code cascade = ALL}로 root 저장에 함께 실리고, 별도
     * Repository를 두지 않는다.
     *
     * <p>{@code @OrderBy}는 옛 {@code findAllByOrderIdOrderByIdAsc}의 정렬을 그대로 유지한다 — hold 생성 후처리가 이 순서의 seatId 목록을 그대로 쓴다.
     */
    @Getter(AccessLevel.NONE)
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<OrderSeat> orderSeats = new ArrayList<>();

    /**
     * PENDING 주문을 만든다. 총액은 인자로 받지 않는다 — {@link #addOrderSeat}가 더한 좌석 단가의 합이 곧 총액이다. 바깥에서 계산한 값을 받으면 좌석 합계와 어긋난 총액을 저장할
     * 수 있다.
     */
    public Order(
            final Long memberId,
            final Long performanceId,
            final String orderKey,
            final String holdKey,
            final LocalDateTime expiresAt,
            final String showTitleSnapshot,
            final LocalDateTime performanceStartAtSnapshot,
            final String venueNameSnapshot) {
        this.memberId = memberId;
        this.performanceId = performanceId;
        this.orderKey = orderKey;
        this.holdKey = holdKey;
        this.status = OrderState.PENDING;
        this.totalAmount = BigDecimal.ZERO;
        this.expiresAt = expiresAt;
        this.showTitleSnapshot = showTitleSnapshot;
        this.performanceStartAtSnapshot = performanceStartAtSnapshot;
        this.venueNameSnapshot = venueNameSnapshot;
    }

    /**
     * 주문 좌석을 aggregate root를 통해서만 만든다. 자식의 {@code order} 역참조를 여기서 채우므로 양방향이 어긋날 여지가 없고, 총액도 여기서만 늘어나므로 좌석 합계와 어긋날 수 없다.
     * 저장은 root 저장에 cascade로 함께 실린다.
     *
     * <p>PENDING일 때만 좌석을 추가할 수 있다 — 종료된 주문의 좌석과 금액은 바뀌지 않는다.
     */
    public OrderSeat addOrderSeat(
            final Long performanceSeatId,
            final Long seatId,
            final BigDecimal unitPrice,
            final String gradeCodeSnapshot,
            final String gradeNameSnapshot,
            final String seatLabelSnapshot) {
        validatePending("좌석을 추가");
        final OrderSeat orderSeat = new OrderSeat(
                this, performanceSeatId, seatId, unitPrice, gradeCodeSnapshot, gradeNameSnapshot, seatLabelSnapshot);
        orderSeats.add(orderSeat);
        this.totalAmount = this.totalAmount.add(unitPrice);
        return orderSeat;
    }

    /** 밖에서 컬렉션을 직접 바꾸지 못하게 읽기 전용 view로 준다 — 좌석 추가는 {@link #addOrderSeat}만 통한다. */
    public List<OrderSeat> getOrderSeats() {
        return Collections.unmodifiableList(orderSeats);
    }

    public void confirm(final LocalDateTime now) {
        validatePending("confirm");
        this.status = OrderState.CONFIRMED;
        this.confirmedAt = now;
    }

    /**
     * 만료로 종료한다. <b>만료 시각이 지나기 전에는 만료시킬 수 없다.</b> 옛 구현은 시각을 보지 않아, 아직 유효한 주문도 만료 경로로 들어오면 그대로 EXPIRED가 됐다 — 사용자가 보고 있는
     * 잔여 시간과 실제 상태가 어긋나는 지점이었다.
     */
    public void expire(final LocalDateTime now) {
        validatePending("expire");
        if (now.isBefore(expiresAt)) {
            throw new IllegalStateException("만료 시각 전에는 만료할 수 없습니다. expiresAt=" + expiresAt + ", now=" + now);
        }
        this.status = OrderState.EXPIRED;
        this.expiredAt = now;
    }

    public void cancel(final LocalDateTime now) {
        validatePending("cancel");
        this.status = OrderState.CANCELED;
        this.canceledAt = now;
    }

    public boolean isPending() {
        return status == OrderState.PENDING;
    }

    /**
     * 지금 만료 처리 대상인가. "이미 만료됐는가"가 아니다 — 상태는 아직 PENDING이고, 만료 시각이 지나 {@link #expire}를 부를 수 있다는 뜻이다. 옛 이름
     * {@code isExpired}는 EXPIRED 상태 여부로 읽혔다.
     */
    public boolean isExpirable(final LocalDateTime now) {
        return isPending() && !now.isBefore(expiresAt);
    }

    private void validatePending(final String action) {
        if (!isPending()) {
            throw new IllegalStateException("PENDING 주문만 " + action + " 할 수 있습니다. currentStatus=" + status);
        }
    }
}
