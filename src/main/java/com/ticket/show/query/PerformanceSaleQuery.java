package com.ticket.show.query;

import static com.ticket.show.domain.QGrade.grade;
import static com.ticket.show.domain.performance.QPerformance.performance;
import static com.ticket.show.domain.performance.QPerformanceGrade.performanceGrade;
import static com.ticket.show.domain.show.QShow.show;
import static com.ticket.show.query.QuerydslTupleColumns.required;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.querydsl.core.Tuple;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.ticket.show.domain.performance.PerformanceSaleContext;

import lombok.RequiredArgsConstructor;

/**
 * {@link com.ticket.show.api.PerformanceSaleCatalogApi}이 쓰는 회차 판매 표시값 조회다. show 자기 DB만 본다 — booking
 * data도, venue data도 여기서 참조하지 않는다. venue 이름·좌석 주소 조합은 {@code
 * PerformanceSaleCatalogService}(application)가 venue module의 공개 계약을 직접 불러서 한다.
 */
@Repository
@RequiredArgsConstructor
public class PerformanceSaleQuery {
    private final JPAQueryFactory queryFactory;

    public Optional<PerformanceSaleContext> findContext(final long performanceId) {
        final Tuple row =
                queryFactory
                        .select(
                                performance.id,
                                show.id,
                                show.title,
                                show.venueId,
                                performance.startTime)
                        .from(performance)
                        .join(show)
                        .on(show.id.eq(performance.showId))
                        .where(performance.id.eq(performanceId))
                        .fetchOne();
        if (row == null) {
            return Optional.empty();
        }

        return Optional.of(
                new PerformanceSaleContext(
                        // performance.id/show.id는 PK라 조회된 행에서는 값이 비어 있을 수 없다.
                        required(row, performance.id),
                        required(row, show.id),
                        row.get(show.title),
                        row.get(show.venueId),
                        row.get(performance.startTime)));
    }

    /** 이 회차에 배정된 모든 PerformanceGrade를 반환한다. */
    public List<PerformanceGradeRow> findPerformanceGrades(final long performanceId) {
        return queryFactory
                .select(
                        com.querydsl.core.types.Projections.constructor(
                                PerformanceGradeRow.class,
                                performanceGrade.id,
                                grade.code,
                                grade.name,
                                performanceGrade.sortOrder,
                                performanceGrade.price))
                .from(performanceGrade)
                .join(grade)
                .on(grade.id.eq(performanceGrade.gradeId))
                .where(performanceGrade.performance.id.eq(performanceId))
                .fetch();
    }

    public record PerformanceGradeRow(
            Long performanceGradeId,
            String gradeCode,
            String gradeName,
            Integer sortOrder,
            BigDecimal price) {}
}
