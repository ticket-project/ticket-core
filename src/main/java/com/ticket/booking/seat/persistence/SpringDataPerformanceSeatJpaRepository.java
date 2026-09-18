package com.ticket.booking.seat.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ticket.booking.seat.domain.PerformanceSeat;
import com.ticket.booking.seat.domain.PerformanceSeatStateSnapshot;

interface SpringDataPerformanceSeatJpaRepository extends JpaRepository<PerformanceSeat, Long> {
    List<PerformanceSeat> findAllByPerformanceIdAndSeatIdIn(
            Long performanceId, Collection<Long> seatIds);

    List<PerformanceSeat> findAllByPerformanceId(Long performanceId);

    List<PerformanceSeat> findAllByPerformanceIdOrderBySeatIdAsc(Long performanceId);

    @Query(
            """
            select new com.ticket.booking.seat.domain.PerformanceSeatStateSnapshot(p.id, p.state)
            from PerformanceSeat p
            where p.performanceId = :performanceId
              and p.seatId = :seatId
            """)
    Optional<PerformanceSeatStateSnapshot> findSeatState(
            @Param("performanceId") Long performanceId, @Param("seatId") Long seatId);
}
