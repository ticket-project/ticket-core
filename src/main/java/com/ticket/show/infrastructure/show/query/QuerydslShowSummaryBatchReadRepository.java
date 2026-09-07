package com.ticket.show.infrastructure.show.query;

import com.querydsl.core.Tuple;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.ticket.show.application.show.query.ShowSummaryBatchReadRepository;
import com.ticket.show.application.show.query.model.ShowSummaryRow;
import com.ticket.show.application.support.VenueDisplays;
import com.ticket.venue.VenueLookup;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.ticket.show.domain.show.QShow.show;

@Repository
@RequiredArgsConstructor
public class QuerydslShowSummaryBatchReadRepository implements ShowSummaryBatchReadRepository {

    private final JPAQueryFactory queryFactory;
    private final VenueLookup venueLookup;

    @Override
    public Map<Long, ShowSummaryRow> findSummaries(final Set<Long> showIds) {
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
                        show.venueId
                )
                .from(show)
                .where(show.id.in(showIds))
                .fetch();

        final VenueDisplays venues = VenueDisplays.load(venueLookup, rows.stream().map(t -> t.get(show.venueId)).toList());

        return rows.stream()
                .map(row -> new ShowSummaryRow(
                        row.get(show.id),
                        row.get(show.title),
                        row.get(show.image),
                        row.get(show.startDate),
                        row.get(show.endDate),
                        venues.nameOf(row.get(show.venueId))
                ))
                .collect(Collectors.toMap(ShowSummaryRow::showId, summary -> summary));
    }
}
