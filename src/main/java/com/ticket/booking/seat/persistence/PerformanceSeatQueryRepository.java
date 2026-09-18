package com.ticket.booking.seat.persistence;

import java.util.List;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.ticket.booking.seat.domain.PerformanceSeat;

import lombok.RequiredArgsConstructor;

/**
 * 회차 좌석 편성(PerformanceSeat)의 booking local 조회를 모두 갖는다 — seat-map, 좌석 상태, 잔여석.
 *
 * <p>세 조회 모두 같은 테이블에서 이 회차의 편성을 읽으므로 한 Repository에 둔다. 조회 결과는 {@code PerformanceSeat} 엔티티이고, 응답 상태
 * 변환과 Redis 점유 반영은 use case가 한다. 다만 <b>트랜잭션 경계는 메서드마다 다르다</b> — 클래스 전체에는 {@code @Transactional}을
 * 붙이지 않는다. {@link #findSeatStates(Long)}만 읽기 전용 트랜잭션을 갖는 이유는 그 메서드의 Javadoc에 있다.
 */
@Repository
@RequiredArgsConstructor
public class PerformanceSeatQueryRepository {
    private final SpringDataPerformanceSeatJpaRepository performanceSeatJpaRepository;

    /**
     * 회차 정적 seat-map에 필요한 판매 편성을 조회한다. 이 회차에 실제로 판매 편성된 좌석만 반환한다 — 편성되지 않은 물리 Seat는 여기 나타나지 않으므로
     * seat-map 응답에서도 자연히 제외된다.
     */
    public List<PerformanceSeat> findAllByPerformanceId(final Long performanceId) {
        return performanceSeatJpaRepository.findAllByPerformanceId(performanceId);
    }

    /**
     * 회차 좌석의 판매 상태를 좌석 id 순서로 읽는다.
     *
     * <p>읽기 전용 트랜잭션을 이 조회 자체가 소유한다. 호출자({@code GetSeatStatusUseCase})는 이 결과에 Redis 점유를 덧씌우는데, 그
     * Redis 조회까지 같은 트랜잭션에 들어가면 Redis 지연만큼 DB connection을 더 쥐게 된다. 경계를 여기 두면 호출자가
     * {@code @Transactional}을 갖지 않아도 DB 읽기는 트랜잭션 안에서, Redis 조회는 밖에서 끝난다.
     *
     * <p>같은 클래스 안에서 이 메서드를 호출하면 self-invocation이라 proxy가 적용되지 않아 경계가 사라진다 — 내부에서 부르지 않는다.
     */
    @Transactional(readOnly = true)
    public List<PerformanceSeat> findSeatStates(final Long performanceId) {
        return performanceSeatJpaRepository.findAllByPerformanceIdOrderBySeatIdAsc(performanceId);
    }

    /**
     * 잔여석 계산에 쓰는 좌석 편성을 좌석 id 순서로 읽는다. 등급 코드/이름/표시순서는 show {@code PerformanceSaleCatalogApi}에서
     * {@code performanceGradeId}로 따로 조합한다 — booking에서 show grade 테이블을 직접 join하지 않는다.
     *
     * <p>{@link #findSeatStates(Long)}와 같은 행을 읽지만 트랜잭션 경계가 다르다 — 이쪽은 {@code
     * SeatAvailabilitySnapshotReader}가 회차 존재 확인과 함께 하나의 트랜잭션으로 묶는다.
     */
    public List<PerformanceSeat> findSeatAvailabilities(final Long performanceId) {
        return performanceSeatJpaRepository.findAllByPerformanceIdOrderBySeatIdAsc(performanceId);
    }
}
