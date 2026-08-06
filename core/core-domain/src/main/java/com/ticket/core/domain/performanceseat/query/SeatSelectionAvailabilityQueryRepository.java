package com.ticket.core.domain.performanceseat.query;

import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.ticket.core.domain.performanceseat.query.model.SeatSelectionAvailabilityView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

import static com.ticket.core.domain.performance.model.QPerformance.performance;
import static com.ticket.core.domain.performanceseat.model.QPerformanceSeat.performanceSeat;

@Repository
@RequiredArgsConstructor
public class SeatSelectionAvailabilityQueryRepository {

    private final JPAQueryFactory queryFactory;

    public Optional<SeatSelectionAvailabilityView> findForSelection(
            final Long performanceId,
            final Long seatId
    ) {
        return Optional.ofNullable(queryFactory
                .select(Projections.constructor(SeatSelectionAvailabilityView.class,
                        performance.orderOpenTime,
                        performance.orderCloseTime,
                        performanceSeat.id,
                        performanceSeat.state
                ))
                .from(performance)
                .leftJoin(performanceSeat).on(
                        performanceSeat.performance.id.eq(performance.id),
                        performanceSeat.seat.id.eq(seatId)
                )
                .where(performance.id.eq(performanceId))
                .fetchOne());
    }
}
