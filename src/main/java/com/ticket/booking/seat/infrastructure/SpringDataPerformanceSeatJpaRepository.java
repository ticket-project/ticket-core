package com.ticket.booking.seat.infrastructure;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ticket.booking.seat.domain.PerformanceSeat;
import com.ticket.booking.seat.domain.PerformanceSeatState;

interface SpringDataPerformanceSeatJpaRepository extends JpaRepository<PerformanceSeat, Long> {
    List<PerformanceSeat> findAllByPerformanceIdAndSeatIdIn(
            Long performanceId, Collection<Long> seatIds);

    List<PerformanceSeat> findAllByStateEquals(PerformanceSeatState state);
}
