package com.ticket.catalog.internal.infrastructure.performance;

import com.querydsl.core.Tuple;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.ticket.catalog.PerformanceSummary;
import com.ticket.catalog.internal.application.performance.query.PerformanceSummaryBatchReadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.ticket.catalog.internal.domain.performance.QPerformance.performance;

@Repository
@RequiredArgsConstructor
public class QuerydslPerformanceSummaryBatchReadRepository implements PerformanceSummaryBatchReadRepository {

    private final JPAQueryFactory queryFactory;

    @Override
    public Map<Long, PerformanceSummary> findSummaries(final Set<Long> performanceIds) {
        if (performanceIds.isEmpty()) {
            return Map.of();
        }

        final List<Tuple> rows = queryFactory
                .select(
                        performance.id,
                        performance.show.id,
                        performance.performanceNo,
                        performance.startTime
                )
                .from(performance)
                .where(performance.id.in(performanceIds))
                .fetch();

        return rows.stream()
                .map(row -> new PerformanceSummary(
                        row.get(performance.id),
                        row.get(performance.show.id),
                        row.get(performance.performanceNo),
                        row.get(performance.startTime)
                ))
                .collect(Collectors.toMap(PerformanceSummary::performanceId, summary -> summary));
    }
}
