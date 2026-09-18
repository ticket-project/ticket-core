package com.ticket.booking.seat.query;

import static com.ticket.booking.seat.domain.QPerformanceSeat.performanceSeat;

import java.util.List;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.ticket.booking.seat.domain.PerformanceSeatState;

import lombok.RequiredArgsConstructor;

/**
 * 회차 좌석의 DB 판매 상태를 읽는다.
 *
 * <p>읽기 전용 트랜잭션을 이 조회 자체가 소유한다. 호출자({@code GetSeatStatusUseCase})는 이 결과에 Redis 점유를 덧씌우는데, 그 Redis
 * 조회까지 같은 트랜잭션에 들어가면 Redis 지연만큼 DB connection을 더 쥐게 된다. 경계를 여기 두면 호출자가 {@code @Transactional}을 갖지
 * 않아도 DB 읽기는 트랜잭션 안에서, Redis 조회는 밖에서 끝난다.
 */
@Repository
@RequiredArgsConstructor
public class SeatStateQuery {
    private final JPAQueryFactory queryFactory;

    @Transactional(readOnly = true)
    public List<SeatStateSnapshotRow> findSeatStates(final Long performanceId) {
        return queryFactory
                .select(
                        Projections.constructor(
                                SeatStateRow.class,
                                performanceSeat.id,
                                performanceSeat.seatId,
                                performanceSeat.state))
                .from(performanceSeat)
                .where(performanceSeat.performanceId.eq(performanceId))
                .orderBy(performanceSeat.seatId.asc())
                .fetch()
                .stream()
                .map(SeatStateRow::toSnapshotRow)
                .toList();
    }

    public record SeatStateRow(Long performanceSeatId, Long seatId, PerformanceSeatState state) {
        private SeatStateSnapshotRow toSnapshotRow() {
            return new SeatStateSnapshotRow(performanceSeatId, seatId, SeatStatus.from(state));
        }
    }
}
