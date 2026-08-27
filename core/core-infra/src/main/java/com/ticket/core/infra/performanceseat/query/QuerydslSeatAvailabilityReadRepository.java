package com.ticket.core.infra.performanceseat.query;

import com.ticket.core.app.performanceseat.query.SeatAvailabilityCalculator;
import com.ticket.core.app.performanceseat.query.SeatAvailabilityReadRepository;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

import static com.ticket.core.domain.performanceseat.model.QPerformanceSeat.performanceSeat;
import static com.ticket.core.domain.show.mapping.QShowGrade.showGrade;
import static com.ticket.core.domain.show.mapping.QShowSeat.showSeat;

@Repository
@RequiredArgsConstructor
public class QuerydslSeatAvailabilityReadRepository implements SeatAvailabilityReadRepository {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<SeatAvailabilityCalculator.AvailableSeatRow> findAvailableSeatRows(Long performanceId, Long showId) {
        return queryFactory
                .select(Projections.constructor(SeatAvailabilityCalculator.AvailableSeatRow.class,
                        performanceSeat.seat.id,
                        performanceSeat.state,
                        showGrade.gradeName,
                        showGrade.sortOrder
                ))
                .from(performanceSeat)
                .join(showSeat).on(
                        showSeat.seat.id.eq(performanceSeat.seat.id),
                        showSeat.show.id.eq(showId)
                )
                .join(showGrade).on(showGrade.id.eq(showSeat.showGrade.id))
                .where(
                        performanceSeat.performance.id.eq(performanceId)
                )
                .orderBy(showGrade.sortOrder.asc(), performanceSeat.seat.id.asc())
                .fetch();
    }
}
