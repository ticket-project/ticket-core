package com.ticket.show.performance.infrastructure;

import com.ticket.show.performance.application.usecase.GetPerformanceSummaryUseCase;

import com.ticket.show.performance.application.port.PerformanceQueryPort;

import com.ticket.show.performance.application.port.PerformanceQueryPort;
import com.querydsl.core.Tuple;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.ticket.show.performance.application.PerformanceSummaryView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

import static com.ticket.show.performance.domain.QPerformance.performance;
import static com.ticket.show.catalog.domain.QShow.show;

/**
 * show 자기 DB에서 회차 요약 데이터를 읽는 persistence adapter다. venue region 표시값 조합은
 * {@code GetPerformanceSummaryUseCase}(application)가 한다 — 여기서는 {@code venueId} scalar만
 * 넘긴다.
 */
@Repository
@RequiredArgsConstructor
public class QuerydslPerformanceQueryPort implements PerformanceQueryPort {

    private final JPAQueryFactory queryFactory;

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

        return Optional.of(new PerformanceSummaryView(
                row.get(show.title),
                row.get(show.venueId),
                row.get(performance.startTime)
        ));
    }
}
