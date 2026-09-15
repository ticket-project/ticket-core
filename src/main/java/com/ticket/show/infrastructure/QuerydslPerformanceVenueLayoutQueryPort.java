package com.ticket.show.infrastructure;

import static com.ticket.show.domain.QGrade.grade;
import static com.ticket.show.domain.performance.QPerformance.performance;
import static com.ticket.show.domain.performance.QPerformanceGrade.performanceGrade;
import static com.ticket.show.domain.show.QShow.show;
import static com.ticket.show.infrastructure.QuerydslTupleColumns.required;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.querydsl.core.Tuple;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.ticket.show.application.port.PerformanceVenueLayoutQueryPort;
import com.ticket.show.domain.performance.PerformanceVenueLayoutContext;

import lombok.RequiredArgsConstructor;

/**
 * {@link PerformanceVenueLayoutQueryPort}의 show 소유 구현이다. show 자기 DB만 본다 — booking data도, venue 좌석
 * 배치 좌표도 여기서 참조하지 않는다. 좌석 배치 좌표·venue 표시값 조합은 {@code
 * PerformanceVenueLayoutCatalogService}(application)가 venue module의 공개 계약을 직접 불러서 한다.
 */
@Repository
@RequiredArgsConstructor
public class QuerydslPerformanceVenueLayoutQueryPort implements PerformanceVenueLayoutQueryPort {
    private final JPAQueryFactory queryFactory;

    @Override
    public Optional<PerformanceVenueLayoutContext> findVenueLayoutContext(
            final long performanceId) {
        final Tuple row =
                queryFactory
                        .select(performance.id, show.venueId)
                        .from(performance)
                        .join(show)
                        .on(show.id.eq(performance.showId))
                        .where(performance.id.eq(performanceId))
                        .fetchOne();
        if (row == null) {
            return Optional.empty();
        }

        return Optional.of(
                new PerformanceVenueLayoutContext(
                        // performance.id는 PK라 조회된 행에서는 값이 비어 있을 수 없다.
                        required(row, performance.id), row.get(show.venueId)));
    }

    @Override
    public Optional<Long> findRepresentativePerformanceIdByShowId(final long showId) {
        return Optional.ofNullable(
                queryFactory
                        .select(performance.id)
                        .from(performance)
                        .where(performance.showId.eq(showId))
                        .orderBy(performance.id.asc())
                        .fetchFirst());
    }

    @Override
    public List<PerformanceGradeLayoutRow> findGradeLayouts(final long performanceId) {
        return queryFactory
                .select(
                        Projections.constructor(
                                PerformanceGradeLayoutRow.class,
                                performanceGrade.id,
                                grade.code,
                                grade.name,
                                performanceGrade.sortOrder))
                .from(performanceGrade)
                .join(grade)
                .on(grade.id.eq(performanceGrade.gradeId))
                .where(performanceGrade.performance.id.eq(performanceId))
                .fetch();
    }
}
