package com.ticket.show.persistence;

import static com.ticket.show.domain.QGrade.grade;
import static com.ticket.show.domain.performance.QPerformance.performance;
import static com.ticket.show.domain.performance.QPerformanceGrade.performanceGrade;
import static com.ticket.show.domain.show.QShow.show;
import static com.ticket.show.persistence.QuerydslTupleColumns.required;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.querydsl.core.Tuple;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.ticket.show.domain.performance.PerformanceSaleContext;
import com.ticket.show.domain.performance.PerformanceVenueLayoutContext;
import com.ticket.show.query.PerformanceGradeView;
import com.ticket.show.query.PerformanceSummaryView;

import lombok.RequiredArgsConstructor;

/**
 * show 자기 DB에서 회차 표시값을 읽는다 — 회차 요약, 회차별 grade 목록, 판매 snapshot 조회({@link
 * com.ticket.show.api.PerformanceSaleCatalogApi}), 정적 seat-map 조회({@link
 * com.ticket.show.api.PerformanceVenueLayoutCatalogApi})가 한 곳에 있다.
 *
 * <p>booking data도, venue data도 여기서 참조하지 않는다 — {@code venueId} scalar만 넘긴다. venue 이름·region·좌석
 * 주소·좌석 배치 좌표 조합은 application({@code GetPerformanceSummaryUseCase}, {@code
 * PerformanceSaleCatalogService}, {@code PerformanceVenueLayoutCatalogService})이 venue module의 공개
 * 계약을 직접 불러서 한다.
 */
@Repository
@RequiredArgsConstructor
public class PerformanceQueryRepository {
    private final JPAQueryFactory queryFactory;

    public Optional<PerformanceSummaryView> findByPerformanceId(final Long performanceId) {
        final Tuple row =
                queryFactory
                        .select(show.title, show.venueId, performance.startTime)
                        .from(performance)
                        .join(show)
                        .on(show.id.eq(performance.showId))
                        .where(performance.id.eq(performanceId))
                        .fetchOne();
        if (row == null) {
            return Optional.empty();
        }

        return Optional.of(
                new PerformanceSummaryView(
                        row.get(show.title),
                        row.get(show.venueId),
                        row.get(performance.startTime)));
    }

    /** 회차별 grade 목록·가격 화면 조회다. aggregate 복원이 아니라 표시용 join 결과를 반환한다. */
    public List<PerformanceGradeView> findAllByPerformanceIdOrderBySortOrderAsc(
            final Long performanceId) {
        return queryFactory
                .select(
                        Projections.constructor(
                                PerformanceGradeView.class,
                                performanceGrade.id,
                                grade.code,
                                grade.name,
                                performanceGrade.price,
                                performanceGrade.sortOrder))
                .from(performanceGrade)
                .join(grade)
                .on(grade.id.eq(performanceGrade.gradeId))
                .where(performanceGrade.performance.id.eq(performanceId))
                .orderBy(performanceGrade.sortOrder.asc())
                .fetch();
    }

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
                        Projections.constructor(
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

    public Optional<Long> findRepresentativePerformanceIdByShowId(final long showId) {
        return Optional.ofNullable(
                queryFactory
                        .select(performance.id)
                        .from(performance)
                        .where(performance.showId.eq(showId))
                        .orderBy(performance.id.asc())
                        .fetchFirst());
    }

    /** 이 회차에 배정된 모든 PerformanceGrade의 표시값을 반환한다. 가격은 담지 않는다. */
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

    public record PerformanceGradeRow(
            Long performanceGradeId,
            String gradeCode,
            String gradeName,
            Integer sortOrder,
            BigDecimal price) {}

    public record PerformanceGradeLayoutRow(
            Long performanceGradeId, String gradeCode, String gradeName, Integer sortOrder) {}
}
