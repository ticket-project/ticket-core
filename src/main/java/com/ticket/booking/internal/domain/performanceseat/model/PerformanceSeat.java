package com.ticket.booking.internal.domain.performanceseat.model;

import com.ticket.booking.internal.domain.BookingAuditedEntity;
import com.ticket.catalog.internal.domain.performance.Performance;
import com.ticket.catalog.internal.domain.seat.Seat;
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "performance_id", nullable = false)
    private Performance performance;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seat_id", nullable = false)
    private Seat seat;

    @Enumerated(EnumType.STRING)
    private PerformanceSeatState state;

    private BigDecimal price;

    public PerformanceSeat(final Performance performance, final Seat seat, final PerformanceSeatState state, final BigDecimal price) {
        this.performance = performance;
        this.seat = seat;
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
