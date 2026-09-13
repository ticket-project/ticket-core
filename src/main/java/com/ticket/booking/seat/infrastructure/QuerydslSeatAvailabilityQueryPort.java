package com.ticket.booking.seat.infrastructure;

import static com.ticket.booking.seat.domain.QPerformanceSeat.performanceSeat;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.ticket.booking.seat.application.port.SeatAvailabilityQueryPort;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class QuerydslSeatAvailabilityQueryPort implements SeatAvailabilityQueryPort {
    private final JPAQueryFactory queryFactory;

    @Override
    public List<PerformanceSeatStateRow> findSeatStates(final Long performanceId) {
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
}
