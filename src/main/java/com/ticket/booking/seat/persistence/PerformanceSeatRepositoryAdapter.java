package com.ticket.booking.seat.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.ticket.booking.seat.domain.PerformanceSeat;
import com.ticket.booking.seat.domain.PerformanceSeatRepository;

import lombok.RequiredArgsConstructor;

/** {@link PerformanceSeatRepository}의 JPA 구현이다. */
@Repository
@RequiredArgsConstructor
public class PerformanceSeatRepositoryAdapter implements PerformanceSeatRepository {
    private final SpringDataPerformanceSeatJpaRepository jpaRepository;

    @Override
    public List<PerformanceSeat> findAllByPerformanceIdAndSeatIdIn(
            final Long performanceId, final Collection<Long> seatIds) {
        return jpaRepository.findAllByPerformanceIdAndSeatIdIn(performanceId, seatIds);
    }

    /** 좌석 한 건만 조회한다. 회차 존재와 예매 가능 시각은 회차 정책이 판정하므로 조인이 필요 없고, UK_PERFORMANCE_SEATS_PERFORMANCE_SEAT 유니크 인덱스를 그대로 탄다. */
    @Override
    public Optional<PerformanceSeat> findSeatState(final Long performanceId, final Long seatId) {
        return jpaRepository.findByPerformanceIdAndSeatId(performanceId, seatId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PerformanceSeat> findAllByPerformanceId(final Long performanceId) {
        return jpaRepository.findAllByPerformanceIdOrderBySeatIdAsc(performanceId);
    }
}
