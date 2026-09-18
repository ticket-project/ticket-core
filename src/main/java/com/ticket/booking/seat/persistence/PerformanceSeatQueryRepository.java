package com.ticket.booking.seat.persistence;

import static com.ticket.booking.seat.domain.QPerformanceSeat.performanceSeat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.querydsl.core.Tuple;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.ticket.booking.seat.domain.PerformanceSeatState;
import com.ticket.booking.seat.query.SeatStateView;
import com.ticket.booking.seat.query.SeatStatus;

import lombok.RequiredArgsConstructor;

/**
 * 회차 좌석 편성(PerformanceSeat)의 booking local 조회를 모두 갖는다 — seat-map, 좌석 상태, 잔여석.
 *
 * <p>세 조회 모두 같은 테이블을 읽고 반환 모양만 다르므로 한 Repository에 둔다. 다만 <b>트랜잭션 경계는 메서드마다 다르다</b> — 클래스 전체에는
 * {@code @Transactional}을 붙이지 않는다. {@link #findSeatStates(Long)}만 읽기 전용 트랜잭션을 갖는 이유는 그 메서드의
 * Javadoc에 있다.
 */
@Repository
@RequiredArgsConstructor
public class PerformanceSeatQueryRepository {
    private final JPAQueryFactory queryFactory;

    /**
     * 회차 정적 seat-map에 필요한 판매 편성을 조회한다. 이 회차에 실제로 판매 편성된 좌석만 반환한다 — 편성되지 않은 물리 Seat는 여기 나타나지 않으므로
     * seat-map 응답에서도 자연히 제외된다.
     */
    public List<PerformanceSeatMapRow> findAllByPerformanceId(final Long performanceId) {
        return queryFactory
                .select(
                        Projections.constructor(
                                PerformanceSeatMapRow.class,
                                performanceSeat.id,
                                performanceSeat.seatId,
                                performanceSeat.performanceGradeId,
                                performanceSeat.unitPrice))
                .from(performanceSeat)
                .where(performanceSeat.performanceId.eq(performanceId))
                .fetch();
    }

    /**
     * 회차 좌석의 DB 판매 상태를 API 상태로 변환해 읽는다.
     *
     * <p>읽기 전용 트랜잭션을 이 조회 자체가 소유한다. 호출자({@code GetSeatStatusUseCase})는 이 결과에 Redis 점유를 덧씌우는데, 그
     * Redis 조회까지 같은 트랜잭션에 들어가면 Redis 지연만큼 DB connection을 더 쥐게 된다. 경계를 여기 두면 호출자가
     * {@code @Transactional}을 갖지 않아도 DB 읽기는 트랜잭션 안에서, Redis 조회는 밖에서 끝난다.
     *
     * <p>같은 클래스 안에서 이 메서드를 호출하면 self-invocation이라 proxy가 적용되지 않아 경계가 사라진다 — 내부에서 부르지 않는다.
     */
    @Transactional(readOnly = true)
    public List<SeatStateView> findSeatStates(final Long performanceId) {
        return queryFactory
                .select(performanceSeat.id, performanceSeat.seatId, performanceSeat.state)
                .from(performanceSeat)
                .where(performanceSeat.performanceId.eq(performanceId))
                .orderBy(performanceSeat.seatId.asc())
                .fetch()
                .stream()
                .map(PerformanceSeatQueryRepository::toSeatStateView)
                .toList();
    }

    /**
     * 회차의 PerformanceSeat 판매 상태와 배정된 PerformanceGrade ID만 조회한다(booking local). 등급 코드/이름/표시순서는 show
     * {@code PerformanceSaleCatalogApi}에서 performanceGradeId로 따로 조합한다 — booking에서 show grade 테이블을
     * 직접 join하지 않는다.
     *
     * <p>원래 이름은 {@code findSeatStates}였다. 한 Repository로 합치면서 파라미터가 같은 {@link
     * #findSeatStates(Long)}와 이름이 충돌해 개명했다.
     */
    public List<PerformanceSeatStateRow> findSeatAvailabilities(final Long performanceId) {
        return queryFactory
                .select(
                        Projections.constructor(
                                PerformanceSeatStateRow.class,
                                performanceSeat.seatId,
                                performanceSeat.state,
                                performanceSeat.performanceGradeId))
                .from(performanceSeat)
                .where(performanceSeat.performanceId.eq(performanceId))
                .orderBy(performanceSeat.seatId.asc())
                .fetch();
    }

    /**
     * {@code Tuple.get}은 "그 slot이 projection에 없을 수 있다"는 이유로 언제나 nullable이지만, 여기 셋은 바로 위에서 select 한
     * PERFORMANCE_SEATS의 NOT NULL 컬럼이라 조회된 행에서는 값이 비어 있을 수 없다.
     */
    private static SeatStateView toSeatStateView(final Tuple tuple) {
        return new SeatStateView(
                Objects.requireNonNull(tuple.get(performanceSeat.id), "performanceSeat.id"),
                Objects.requireNonNull(tuple.get(performanceSeat.seatId), "performanceSeat.seatId"),
                SeatStatus.from(
                        Objects.requireNonNull(
                                tuple.get(performanceSeat.state), "performanceSeat.state")));
    }

    public record PerformanceSeatMapRow(
            Long performanceSeatId, Long seatId, Long performanceGradeId, BigDecimal unitPrice) {}

    public record PerformanceSeatStateRow(
            Long seatId, PerformanceSeatState state, Long performanceGradeId) {}
}
