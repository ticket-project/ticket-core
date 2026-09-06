package com.ticket.booking.infrastructure.performanceseat;

import com.ticket.booking.domain.performanceseat.model.PerformanceSeat;
import com.ticket.booking.domain.performanceseat.model.PerformanceSeatState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

interface SpringDataPerformanceSeatJpaRepository extends JpaRepository<PerformanceSeat, Long> {

    List<PerformanceSeat> findAllByPerformanceIdAndSeatIdIn(Long performanceId, Collection<Long> seatIds);

    List<PerformanceSeat> findAllByStateEquals(PerformanceSeatState state);
}
