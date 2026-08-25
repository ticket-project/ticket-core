package com.ticket.core.infra.performanceseat.query;

import com.ticket.core.domain.performanceseat.query.SeatSelectionAvailabilityQueryRepository;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.ticket.core.domain.performanceseat.query.model.SeatSelectionAvailabilityView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

import static com.ticket.core.domain.performanceseat.model.QPerformanceSeat.performanceSeat;

@Repository
@RequiredArgsConstructor
public class QuerydslSeatSelectionAvailabilityQueryRepository implements SeatSelectionAvailabilityQueryRepository {

    private final JPAQueryFactory queryFactory;

    /**
     * 좌석 한 건만 조회한다. 회차 존재와 예매 가능 시각은 회차 정책이 판정하므로 조인이 필요 없고,
     * UK_PERFORMANCE_SEATS_PERFORMANCE_SEAT 유니크 인덱스를 그대로 탄다.
     */
    @Override
    public Optional<SeatSelectionAvailabilityView> findSelectableSeat(
            final Long performanceId,
            final Long seatId
    ) {
        return Optional.ofNullable(queryFactory
                .select(Projections.constructor(SeatSelectionAvailabilityView.class,
                        performanceSeat.id,
                        performanceSeat.state
                ))
                .from(performanceSeat)
                .where(
                        performanceSeat.performance.id.eq(performanceId),
                        performanceSeat.seat.id.eq(seatId)
                )
                .fetchOne());
    }
}
