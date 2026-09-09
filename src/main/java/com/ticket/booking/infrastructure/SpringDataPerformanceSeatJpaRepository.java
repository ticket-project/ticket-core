package com.ticket.booking.infrastructure;

import com.ticket.booking.domain.PerformanceSeat;
import com.ticket.booking.domain.PerformanceSeatState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

interface SpringDataPerformanceSeatJpaRepository extends JpaRepository<PerformanceSeat, Long> {

    List<PerformanceSeat> findAllByPerformanceIdAndSeatIdIn(Long performanceId, Collection<Long> seatIds);

    List<PerformanceSeat> findAllByStateEquals(PerformanceSeatState state);
}
