package com.ticket.booking.domain;

import com.ticket.booking.domain.BookingAuditedEntity;
import com.ticket.booking.domain.PerformanceSeatState;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 특정 Performance에서 판매하는 특정 Seat다. 어느 PerformanceGrade에 속하는지와 판매 시점에 확정된
 * {@code unitPrice}(PerformanceGrade.price의 snapshot)를 갖는다. 판매 좌석이 생성된 뒤
 * {@code unitPrice}는 바꾸지 않는다 — 설계 배경은
 * {@code docs/adr/0005-performance-grade-price-ownership-and-payment-ticketing-modules.md}를 본다.
 *
 * <p>{@code performanceId}/{@code seatId}/{@code performanceGradeId}는 모두 show aggregate를
 * 가리키는 cross-module scalar ID다. JPA 연관관계로 show entity를 참조하지 않는다.
 */
@Getter
@Entity
@Table(
        name = "PERFORMANCE_SEATS",
        uniqueConstraints = @UniqueConstraint(
                name = "UK_PERFORMANCE_SEATS_PERFORMANCE_SEAT",
                columnNames = {"performance_id", "seat_id"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PerformanceSeat extends BookingAuditedEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "performance_id", nullable = false)
    private Long performanceId;

    @Column(name = "seat_id", nullable = false)
    private Long seatId;

    @Column(name = "performance_grade_id", nullable = false)
    private Long performanceGradeId;

    @Enumerated(EnumType.STRING)
    private PerformanceSeatState state;

    @Column(name = "unit_price", nullable = false, precision = 19, scale = 2)
    private BigDecimal unitPrice;

    @Version
    @Column(nullable = false)
    private Long version;

    public PerformanceSeat(
            final Long performanceId,
            final Long seatId,
            final Long performanceGradeId,
            final PerformanceSeatState state,
            final BigDecimal unitPrice
    ) {
        this.performanceId = performanceId;
        this.seatId = seatId;
        this.performanceGradeId = performanceGradeId;
        this.state = state;
        this.unitPrice = unitPrice;
    }

    public void reserve() {
        this.state = PerformanceSeatState.RESERVED;
    }

    public void release() {
        this.state = PerformanceSeatState.AVAILABLE;
    }
}
