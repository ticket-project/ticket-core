package com.ticket.booking.internal.infrastructure.performanceseat.query;

import com.ticket.booking.internal.application.performanceseat.query.model.AvailableSeatRow;

import com.ticket.booking.internal.application.performanceseat.query.SeatAvailabilityCalculator;
import com.ticket.booking.internal.application.performanceseat.query.SeatAvailabilityReadRepository;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

import static com.ticket.booking.internal.domain.performanceseat.model.QPerformanceSeat.performanceSeat;
import static com.ticket.catalog.internal.domain.show.QShowGrade.showGrade;
import static com.ticket.catalog.internal.domain.show.QShowSeat.showSeat;

@Repository
@RequiredArgsConstructor
public class QuerydslSeatAvailabilityReadRepository implements SeatAvailabilityReadRepository {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<AvailableSeatRow> findAvailableSeatRows(Long performanceId, Long showId) {
        return queryFactory
                .select(Projections.constructor(AvailableSeatRow.class,
                        performanceSeat.seatId,
                        performanceSeat.state,
                        showGrade.gradeName,
                        showGrade.sortOrder
                ))
                .from(performanceSeat)
                .join(showSeat).on(
                        showSeat.seat.id.eq(performanceSeat.seatId),
                        showSeat.show.id.eq(showId)
                )
                .join(showGrade).on(showGrade.id.eq(showSeat.showGrade.id))
                .where(
                        performanceSeat.performanceId.eq(performanceId)
                )
                .orderBy(showGrade.sortOrder.asc(), performanceSeat.seatId.asc())
                .fetch();
    }
}
