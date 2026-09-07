package com.ticket.show.infrastructure.performance.query;

import com.ticket.show.application.performance.query.PerformanceGradeReadRepository;
import com.ticket.show.application.performance.query.model.PerformanceGradeView;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

import static com.ticket.show.domain.grade.QGrade.grade;
import static com.ticket.show.domain.performance.QPerformanceGrade.performanceGrade;

@Repository
@RequiredArgsConstructor
public class QuerydslPerformanceGradeReadRepository implements PerformanceGradeReadRepository {

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
                .join(performanceGrade.grade, grade)
                .where(performanceGrade.performance.id.eq(performanceId))
                .orderBy(performanceGrade.sortOrder.asc())
                .fetch();
    }
}
