package com.ticket.show.catalog.infrastructure;

import com.ticket.show.catalog.application.port.ShowSummaryBatchQueryPort;

import com.querydsl.core.Tuple;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.ticket.show.catalog.application.port.ShowSummaryBatchQueryPort;
import com.ticket.show.catalog.application.ShowSummaryRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.ticket.show.catalog.domain.QShow.show;

/**
 * show 자기 DB에서 공연 표시값을 배치 조회하는 persistence adapter다. venue 표시값 조합은
 * 여기서 하지 않는다 — {@code venueId} scalar만 담아 넘기고, 실제 venue 조회는 이 포트를 부르는
 * use case(application)가 한다.
 */
@Repository
@RequiredArgsConstructor
public class QuerydslShowSummaryBatchQueryPort implements ShowSummaryBatchQueryPort {

    private final JPAQueryFactory queryFactory;

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

        return rows.stream()
                .map(row -> new ShowSummaryRow(
                        row.get(show.id),
                        row.get(show.title),
                        row.get(show.image),
                        row.get(show.startDate),
                        row.get(show.endDate),
                        row.get(show.venueId)
                ))
                .collect(Collectors.toMap(ShowSummaryRow::showId, summary -> summary));
    }
}
