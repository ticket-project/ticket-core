package com.ticket.show.infrastructure;

import static com.ticket.show.domain.QGrade.grade;
import static com.ticket.show.domain.performance.QPerformanceGrade.performanceGrade;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.ticket.show.application.port.PerformanceGradeQueryPort;
import com.ticket.show.application.query.PerformanceGradeView;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class QuerydslPerformanceGradeQueryAdapter implements PerformanceGradeQueryPort {
    private final JPAQueryFactory queryFactory;

    @Override
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
}
