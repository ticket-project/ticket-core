package com.ticket.booking.infrastructure.performanceseat.query;

import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.ticket.booking.application.performanceseat.query.PerformanceSeatMapReadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

import static com.ticket.booking.domain.performanceseat.model.QPerformanceSeat.performanceSeat;

@Repository
@RequiredArgsConstructor
public class QuerydslPerformanceSeatMapReadRepository implements PerformanceSeatMapReadRepository {

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
