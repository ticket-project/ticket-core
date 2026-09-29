package com.ticket.booking.seat.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ticket.booking.seat.domain.PerformanceSeat;

interface SpringDataPerformanceSeatJpaRepository extends JpaRepository<PerformanceSeat, Long> {
    List<PerformanceSeat> findAllByPerformanceIdAndSeatIdIn(Long performanceId, Collection<Long> seatIds);

    List<PerformanceSeat> findAllByPerformanceId(Long performanceId);

    List<PerformanceSeat> findAllByPerformanceIdOrderBySeatIdAsc(Long performanceId);

    Optional<PerformanceSeat> findByPerformanceIdAndSeatId(Long performanceId, Long seatId);
}
