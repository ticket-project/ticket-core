package com.ticket.show.infrastructure.performance.query;

import com.ticket.show.application.performance.query.PerformanceReadRepository;
import com.querydsl.core.Tuple;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.ticket.show.application.performance.query.model.PerformanceSummaryView;
import com.ticket.venue.VenueLookup;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

import static com.ticket.show.domain.performance.QPerformance.performance;
import static com.ticket.show.domain.show.QShow.show;

@Repository
@RequiredArgsConstructor
public class QuerydslPerformanceReadRepository implements PerformanceReadRepository {

    private final JPAQueryFactory queryFactory;
    private final VenueLookup venueLookup;

    @Override
    public Optional<PerformanceSummaryView> findByPerformanceId(final Long performanceId) {
        final Tuple row = queryFactory
                .select(show.title, show.venueId, performance.startTime)
                .from(performance)
                .join(show).on(show.id.eq(performance.showId))
                .where(performance.id.eq(performanceId))
                .fetchOne();
        if (row == null) {
            return Optional.empty();
        }

        final Long venueId = row.get(show.venueId);
        final var region = venueId == null ? null : venueLookup.findSummary(venueId).map(v -> v.region()).orElse(null);

        return Optional.of(new PerformanceSummaryView(
                row.get(show.title),
                region,
                row.get(performance.startTime)
        ));
    }
}
