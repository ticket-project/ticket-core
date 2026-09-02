package com.ticket.booking.internal.domain.performanceseat.model;

import com.ticket.booking.internal.domain.BookingAuditedEntity;
import com.ticket.booking.internal.domain.performanceseat.model.PerformanceSeatState;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

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

    @Enumerated(EnumType.STRING)
    private PerformanceSeatState state;

    private BigDecimal price;

    public PerformanceSeat(final Long performanceId, final Long seatId, final PerformanceSeatState state, final BigDecimal price) {
        this.performanceId = performanceId;
        this.seatId = seatId;
        this.state = state;
        this.price = price;
    }

    public void reserve() {
        this.state = PerformanceSeatState.RESERVED;
    }


    public void release() {
        this.state = PerformanceSeatState.AVAILABLE;
    }
}
