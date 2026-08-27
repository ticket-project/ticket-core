package com.ticket.core.infra.performance.query;

import com.ticket.core.app.performance.query.PerformanceReadRepository;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.ticket.core.app.performance.query.model.PerformanceSummaryView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

import static com.ticket.core.domain.performance.model.QPerformance.performance;
import static com.ticket.core.domain.show.model.QShow.show;
import static com.ticket.core.domain.show.venue.QVenue.venue;

@Repository
@RequiredArgsConstructor
public class QuerydslPerformanceReadRepository implements PerformanceReadRepository {

    private final JPAQueryFactory queryFactory;

    @Override
    public Optional<PerformanceSummaryView> findByPerformanceId(final Long performanceId) {
        return Optional.ofNullable(queryFactory
                .select(Projections.constructor(PerformanceSummaryView.class,
                        show.title,
                        venue.region,
                        performance.startTime,
                        performance.maxCanHoldCount
                ))
                .from(performance)
                .join(performance.show, show)
                .leftJoin(show.venue, venue)
                .where(performance.id.eq(performanceId))
                .fetchOne());
    }
}
