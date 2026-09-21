package com.ticket.booking.seat.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.ticket.booking.seat.domain.PerformanceSeat;
import com.ticket.booking.seat.domain.PerformanceSeatRepository;
import com.ticket.booking.seat.domain.PerformanceSeatStateSnapshot;

import lombok.RequiredArgsConstructor;

/**
 * {@link PerformanceSeatRepository}의 JPA 구현이다.
 *
 * <p><b>클래스 전체에는 {@code @Transactional}을 붙이지 않는다</b> — 조회마다 경계가 다르다. {@link #findSeatStates(Long)}만
 * 읽기 전용 트랜잭션을 갖는 이유는 계약의 Javadoc에 있다. 같은 클래스 안에서 그 메서드를 호출하면 self-invocation이라 proxy가 적용되지 않아 경계가
 * 사라진다 — 내부에서 부르지 않는다.
 */
@Repository
@RequiredArgsConstructor
public class PerformanceSeatRepositoryAdapter implements PerformanceSeatRepository {
    private final SpringDataPerformanceSeatJpaRepository jpaRepository;

    @Override
    public List<PerformanceSeat> saveAll(final List<PerformanceSeat> performanceSeats) {
        return jpaRepository.saveAll(performanceSeats);
    }

    @Override
    public List<PerformanceSeat> findAllByPerformanceIdAndSeatIdIn(
            final Long performanceId, final Collection<Long> seatIds) {
        return jpaRepository.findAllByPerformanceIdAndSeatIdIn(performanceId, seatIds);
    }

    /**
     * 좌석 한 건만 조회한다. 회차 존재와 예매 가능 시각은 회차 정책이 판정하므로 조인이 필요 없고, UK_PERFORMANCE_SEATS_PERFORMANCE_SEAT
     * 유니크 인덱스를 그대로 탄다.
     */
    @Override
    public Optional<PerformanceSeatStateSnapshot> findSeatState(
            final Long performanceId, final Long seatId) {
        return jpaRepository.findSeatState(performanceId, seatId);
    }

    @Override
    public List<PerformanceSeat> findAllByPerformanceId(final Long performanceId) {
        return jpaRepository.findAllByPerformanceId(performanceId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PerformanceSeat> findSeatStates(final Long performanceId) {
        return jpaRepository.findAllByPerformanceIdOrderBySeatIdAsc(performanceId);
    }

    @Override
    public List<PerformanceSeat> findSeatAvailabilities(final Long performanceId) {
        return jpaRepository.findAllByPerformanceIdOrderBySeatIdAsc(performanceId);
    }
}
