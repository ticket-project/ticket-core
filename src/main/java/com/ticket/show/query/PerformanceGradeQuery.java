package com.ticket.show.query;

import static com.ticket.show.domain.QGrade.grade;
import static com.ticket.show.domain.performance.QPerformanceGrade.performanceGrade;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;

import lombok.RequiredArgsConstructor;

/** 회차별 grade 목록·가격 화면 조회다. aggregate 복원이 아니라 표시용 join 결과를 반환한다. */
@Repository
@RequiredArgsConstructor
public class PerformanceGradeQuery {
    private final JPAQueryFactory queryFactory;

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
