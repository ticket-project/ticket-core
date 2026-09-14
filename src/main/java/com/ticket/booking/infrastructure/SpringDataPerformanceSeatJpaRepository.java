package com.ticket.booking.infrastructure;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ticket.booking.domain.seat.PerformanceSeat;

interface SpringDataPerformanceSeatJpaRepository extends JpaRepository<PerformanceSeat, Long> {
    List<PerformanceSeat> findAllByPerformanceIdAndSeatIdIn(
            Long performanceId, Collection<Long> seatIds);
}
