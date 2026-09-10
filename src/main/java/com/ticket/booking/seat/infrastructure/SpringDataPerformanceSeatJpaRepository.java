package com.ticket.booking.seat.infrastructure;

import com.ticket.booking.seat.domain.PerformanceSeat;
import com.ticket.booking.seat.domain.PerformanceSeatState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

interface SpringDataPerformanceSeatJpaRepository extends JpaRepository<PerformanceSeat, Long> {

    List<PerformanceSeat> findAllByPerformanceIdAndSeatIdIn(Long performanceId, Collection<Long> seatIds);

    List<PerformanceSeat> findAllByStateEquals(PerformanceSeatState state);
}
