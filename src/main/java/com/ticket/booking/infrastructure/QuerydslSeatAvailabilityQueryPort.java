package com.ticket.booking.infrastructure;

import com.ticket.booking.application.port.SeatAvailabilityQueryPort;

import com.ticket.booking.application.port.SeatAvailabilityQueryPort;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

import static com.ticket.booking.domain.QPerformanceSeat.performanceSeat;

@Repository
@RequiredArgsConstructor
public class QuerydslSeatAvailabilityQueryPort implements SeatAvailabilityQueryPort {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<PerformanceSeatStateRow> findSeatStates(final Long performanceId) {
        return queryFactory
                .select(Projections.constructor(PerformanceSeatStateRow.class,
                        performanceSeat.seatId,
                        performanceSeat.state,
                        performanceSeat.performanceGradeId
                ))
                .from(performanceSeat)
                .where(performanceSeat.performanceId.eq(performanceId))
                .orderBy(performanceSeat.seatId.asc())
                .fetch();
    }
}
