package com.ticket.booking.seat.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.Repository;
import org.springframework.transaction.annotation.Transactional;

/** 회차 좌석 aggregate의 복원과 booking local 조회를 담당한다. 응답 변환과 Redis 점유 반영은 use case가 한다. 구현은 Spring Data JPA가 만든다. */
public interface PerformanceSeatRepository extends Repository<PerformanceSeat, Long> {
    List<PerformanceSeat> findAllByPerformanceIdAndSeatIdIn(Long performanceId, Collection<Long> seatIds);

    /**
     * 좌석 선택 판정에 쓰는 좌석 한 건을 UK_PERFORMANCE_SEATS_PERFORMANCE_SEAT 유니크 인덱스로 조회한다. 회차 존재와 예매 가능 시각은 회차 정책이 판정하므로 조인이 필요
     * 없다.
     */
    default Optional<PerformanceSeat> findSeatState(final Long performanceId, final Long seatId) {
        return findByPerformanceIdAndSeatId(performanceId, seatId);
    }

    Optional<PerformanceSeat> findByPerformanceIdAndSeatId(Long performanceId, Long seatId);

    /** 판매 편성된 좌석을 seatId 오름차순으로 읽는다. 짧은 읽기 트랜잭션을 열거나 호출자의 트랜잭션에 합류한다. */
    default List<PerformanceSeat> findAllByPerformanceId(final Long performanceId) {
        return findAllByPerformanceIdOrderBySeatIdAsc(performanceId);
    }

    @Transactional(readOnly = true)
    List<PerformanceSeat> findAllByPerformanceIdOrderBySeatIdAsc(Long performanceId);
}
