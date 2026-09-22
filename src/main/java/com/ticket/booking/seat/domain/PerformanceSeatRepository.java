package com.ticket.booking.seat.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 회차 좌석 aggregate의 복원과 booking local 조회를 담당하는 도메인 Repository다.
 *
 * <p>조회는 {@code PerformanceSeat} 엔티티를 돌려주고, 응답 상태 변환과 Redis 점유 반영은 use case가 한다. 다만 <b>트랜잭션 경계는 메서드마다 다르다</b> — 어느 조회가
 * 스스로 경계를 갖는지는 각 메서드의 Javadoc에 있다.
 */
public interface PerformanceSeatRepository {
    /**
     * 판매 좌석 편성(PerformanceSeat 생성)을 저장한다. 이미 존재하는 (performanceId, seatId) 조합은 호출하는 유스케이스가 저장 전에 걸러낸다 — 이 메서드는 DB unique
     * 제약을 최후 방어선으로만 둔다.
     */
    List<PerformanceSeat> saveAll(List<PerformanceSeat> performanceSeats);

    List<PerformanceSeat> findAllByPerformanceIdAndSeatIdIn(Long performanceId, Collection<Long> seatIds);

    /**
     * 좌석 선택 판정에 필요한 좌석 한 건의 상태만 반환한다.
     *
     * <p>고빈도 경로라 엔티티 전체를 적재하지 않고 유니크 인덱스를 그대로 타도록 좁혀 조회한다.
     */
    Optional<PerformanceSeatStateSnapshot> findSeatState(Long performanceId, Long seatId);

    /**
     * 회차 정적 seat-map에 필요한 판매 편성을 조회한다. 이 회차에 실제로 판매 편성된 좌석만 반환한다 — 편성되지 않은 물리 Seat는 여기 나타나지 않으므로 seat-map 응답에서도 자연히
     * 제외된다.
     */
    List<PerformanceSeat> findAllByPerformanceId(Long performanceId);

    /**
     * 회차 좌석의 판매 상태를 좌석 id 순서로 읽는다.
     *
     * <p>읽기 전용 트랜잭션을 이 조회 자체가 소유한다. 호출자({@code GetSeatStatusUseCase})는 이 결과에 Redis 점유를 덧씌우는데, 그 Redis 조회까지 같은 트랜잭션에
     * 들어가면 Redis 지연만큼 DB connection을 더 쥐게 된다. 경계를 여기 두면 호출자가 {@code @Transactional}을 갖지 않아도 DB 읽기는 트랜잭션 안에서, Redis 조회는
     * 밖에서 끝난다.
     */
    List<PerformanceSeat> findSeatStates(Long performanceId);

    /**
     * 잔여석 계산에 쓰는 좌석 편성을 좌석 id 순서로 읽는다. 등급 코드/이름/표시순서는 show {@code PerformanceSaleCatalogApi}에서
     * {@code performanceGradeId}로 따로 조합한다 — booking에서 show grade 테이블을 직접 join하지 않는다.
     *
     * <p>{@link #findSeatStates(Long)}와 같은 행을 읽지만 트랜잭션 경계가 다르다 — 이쪽은 {@code SeatAvailabilitySnapshotReader}가 회차 존재 확인과
     * 함께 하나의 트랜잭션으로 묶는다.
     */
    List<PerformanceSeat> findSeatAvailabilities(Long performanceId);
}
