package com.ticket.catalog.internal.infrastructure.show.query;

import com.querydsl.core.Tuple;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.ticket.catalog.ShowSummary;
import com.ticket.catalog.internal.application.show.query.ShowSummaryBatchReadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.ticket.catalog.internal.domain.show.QShow.show;
import static com.ticket.catalog.internal.domain.show.QVenue.venue;

@Repository
@RequiredArgsConstructor
public class QuerydslShowSummaryBatchReadRepository implements ShowSummaryBatchReadRepository {

    private final JPAQueryFactory queryFactory;

    @Override
    public Map<Long, ShowSummary> findSummaries(final Set<Long> showIds) {
        if (showIds.isEmpty()) {
            return Map.of();
        }

        final List<Tuple> rows = queryFactory
                .select(
                        show.id,
                        show.title,
                        show.image,
                        show.startDate,
                        show.endDate,
                        venue.name
                )
                .from(show)
                .leftJoin(show.venue, venue)
                .where(show.id.in(showIds))
                .fetch();

        return rows.stream()
                .map(row -> new ShowSummary(
                        row.get(show.id),
                        row.get(show.title),
                        row.get(show.image),
                        row.get(show.startDate),
                        row.get(show.endDate),
                        row.get(venue.name)
                ))
                .collect(Collectors.toMap(ShowSummary::showId, summary -> summary));
    }
}
