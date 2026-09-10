package com.ticket.booking.order.domain;

import com.ticket.booking.support.domain.BookingAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Index;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@Entity
@Table(
        name = "ORDER_SEATS",
        uniqueConstraints = @UniqueConstraint(
                name = "UK_ORDER_SEATS_ORDER_PERFORMANCE_SEAT",
                columnNames = {"order_id", "performance_seat_id"}
        ),
        indexes = {
                @Index(name = "IDX_ORDER_SEATS_ORDER_ID", columnList = "order_id"),
                @Index(name = "IDX_ORDER_SEATS_PERFORMANCE_SEAT_ID", columnList = "performance_seat_id")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderSeat extends BookingAuditedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(nullable = false)
    private Long performanceSeatId;

    @Column(nullable = false)
    private Long seatId;

    @Column(name = "price", nullable = false, precision = 19, scale = 2)
    private BigDecimal unitPrice;

    /**
     * 주문 생성 시점의 표시 snapshot이다(ADR 0005). show의 등급·좌석 표시값이 나중에 바뀌어도
     * 이미 만든 주문의 좌석 표시는 바뀌지 않아야 하므로 이 값을 그대로 쓴다.
     */
    @Column(name = "grade_code_snapshot", nullable = false)
    private String gradeCodeSnapshot;

    @Column(name = "grade_name_snapshot", nullable = false)
    private String gradeNameSnapshot;

    @Column(name = "seat_label_snapshot", nullable = false)
    private String seatLabelSnapshot;

    public OrderSeat(
            final Order order,
            final Long performanceSeatId,
            final Long seatId,
            final BigDecimal unitPrice,
            final String gradeCodeSnapshot,
            final String gradeNameSnapshot,
            final String seatLabelSnapshot
    ) {
        this.order = order;
        this.performanceSeatId = performanceSeatId;
        this.seatId = seatId;
        this.unitPrice = unitPrice;
        this.gradeCodeSnapshot = gradeCodeSnapshot;
        this.gradeNameSnapshot = gradeNameSnapshot;
        this.seatLabelSnapshot = seatLabelSnapshot;
    }
}
