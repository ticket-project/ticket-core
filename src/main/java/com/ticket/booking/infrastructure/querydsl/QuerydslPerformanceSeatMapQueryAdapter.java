package com.ticket.booking.infrastructure.querydsl;

import static com.ticket.booking.domain.seat.QPerformanceSeat.performanceSeat;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.ticket.booking.application.port.PerformanceSeatMapQueryPort;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class QuerydslPerformanceSeatMapQueryAdapter implements PerformanceSeatMapQueryPort {
    private final JPAQueryFactory queryFactory;

    @Override
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
}
