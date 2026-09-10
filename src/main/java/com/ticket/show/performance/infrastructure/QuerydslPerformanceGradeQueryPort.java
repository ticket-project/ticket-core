package com.ticket.show.performance.infrastructure;

import com.ticket.show.performance.application.port.PerformanceGradeQueryPort;

import com.ticket.show.performance.application.port.PerformanceGradeQueryPort;
import com.ticket.show.performance.application.PerformanceGradeView;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

import static com.ticket.show.performance.domain.QGrade.grade;
import static com.ticket.show.performance.domain.QPerformanceGrade.performanceGrade;

@Repository
@RequiredArgsConstructor
public class QuerydslPerformanceGradeQueryPort implements PerformanceGradeQueryPort {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<PerformanceGradeView> findAllByPerformanceIdOrderBySortOrderAsc(final Long performanceId) {
        return queryFactory
                .select(Projections.constructor(PerformanceGradeView.class,
                        performanceGrade.id,
                        grade.code,
                        grade.name,
                        performanceGrade.price,
                        performanceGrade.sortOrder
                ))
                .from(performanceGrade)
                .join(grade).on(grade.id.eq(performanceGrade.gradeId))
                .where(performanceGrade.performance.id.eq(performanceId))
                .orderBy(performanceGrade.sortOrder.asc())
                .fetch();
    }
}
