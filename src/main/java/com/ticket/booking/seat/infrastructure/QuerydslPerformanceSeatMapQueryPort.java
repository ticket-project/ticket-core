package com.ticket.booking.seat.infrastructure;

import com.ticket.booking.seat.application.port.PerformanceSeatMapQueryPort;

import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.ticket.booking.seat.application.port.PerformanceSeatMapQueryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

import static com.ticket.booking.seat.domain.QPerformanceSeat.performanceSeat;

@Repository
@RequiredArgsConstructor
public class QuerydslPerformanceSeatMapQueryPort implements PerformanceSeatMapQueryPort {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<PerformanceSeatMapRow> findAllByPerformanceId(final Long performanceId) {
        return queryFactory
                .select(Projections.constructor(PerformanceSeatMapRow.class,
                        performanceSeat.id,
                        performanceSeat.seatId,
                        performanceSeat.performanceGradeId,
                        performanceSeat.unitPrice
                ))
                .from(performanceSeat)
                .where(performanceSeat.performanceId.eq(performanceId))
                .fetch();
    }
}
