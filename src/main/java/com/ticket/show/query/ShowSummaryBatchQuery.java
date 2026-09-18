package com.ticket.show.query;

import static com.ticket.show.domain.show.QShow.show;
import static com.ticket.show.query.QuerydslTupleColumns.required;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Repository;

import com.querydsl.core.Tuple;
import com.querydsl.jpa.impl.JPAQueryFactory;

import lombok.RequiredArgsConstructor;

/**
 * showId 집합으로 공연 표시값을 배치 조회한다. 내 찜 목록처럼 show 내부의 다른 use case가 자기 show 데이터를 조회할 때 쓴다.
 *
 * <p>venue 표시값 조합은 여기서 하지 않는다 — {@code venueId} scalar만 담아 넘기고, 실제 venue 조회는 이 조회를 부르는 use
 * case(application)가 한다.
 */
@Repository
@RequiredArgsConstructor
public class ShowSummaryBatchQuery {
    private final JPAQueryFactory queryFactory;

    /** 빈 {@code showIds}는 빈 map을 반환한다. */
    public Map<Long, ShowSummaryRow> findSummaries(final Set<Long> showIds) {
        if (showIds.isEmpty()) {
            return Map.of();
        }

        final List<Tuple> rows =
                queryFactory
                        .select(
                                show.id,
                                show.title,
                                show.image,
                                show.startDate,
                                show.endDate,
                                show.venueId)
                        .from(show)
                        .where(show.id.in(showIds))
                        .fetch();

        return rows.stream()
                .map(
                        row ->
                                new ShowSummaryRow(
                                        required(row, show.id),
                                        row.get(show.title),
                                        row.get(show.image),
                                        row.get(show.startDate),
                                        row.get(show.endDate),
                                        row.get(show.venueId)))
                .collect(Collectors.toMap(ShowSummaryRow::showId, summary -> summary));
    }
}
