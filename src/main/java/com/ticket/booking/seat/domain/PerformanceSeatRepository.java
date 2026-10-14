package com.ticket.booking.seat.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * 회차 좌석 aggregate의 복원과 booking local 조회를 담당한다. 응답 변환과 Redis 점유 반영은 use case가 한다. 구현은 Spring Data JPA가 만든다.
 *
 * <p>트랜잭션은 호출자가 정한다. 여기 선언한 조회는 바깥 트랜잭션이 있으면 그 connection을 같이 쓰고, 없으면 자동 커밋으로 한 번 실행하고 connection을 바로 돌려준다.
 */
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

    /** 판매 편성된 좌석을 seatId 오름차순으로 읽는다. 엔티티를 많이 읽으므로 호출자가 읽기 전용 트랜잭션 안에서 부른다. */
    @Query("select s from PerformanceSeat s where s.performanceId = :performanceId order by s.seatId")
    List<PerformanceSeat> findAllByPerformanceId(@Param("performanceId") Long performanceId);

    /** 좌석 상태 응답에 쓰는 세 열만 seatId 오름차순으로 읽는다. 엔티티로 읽지 않는 이유는 {@link PerformanceSeatStateRow}에 있다. */
    @Query("""
            select new com.ticket.booking.seat.domain.PerformanceSeatStateRow(s.id, s.seatId, s.state)
            from PerformanceSeat s
            where s.performanceId = :performanceId
            order by s.seatId
            """)
    List<PerformanceSeatStateRow> findStatesByPerformanceId(@Param("performanceId") Long performanceId);

    /** 판매 확정(RESERVED)된 좌석 번호만 읽는다. 상태 조건을 바인딩 값이 아닌 상수로 써야 PostgreSQL이 부분 인덱스 idx_performance_seats_reserved를 고른다. */
    @Query("""
            select s.seatId from PerformanceSeat s
            where s.performanceId = :performanceId
              and s.state = com.ticket.booking.seat.domain.PerformanceSeatState.RESERVED
            """)
    List<Long> findReservedSeatIds(@Param("performanceId") Long performanceId);
}
