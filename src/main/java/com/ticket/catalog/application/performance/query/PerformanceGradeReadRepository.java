package com.ticket.catalog.application.performance.query;

import com.ticket.catalog.application.performance.query.model.PerformanceGradeView;

import java.util.List;

/**
 * 회차별 grade 목록·가격 화면 조회 전용 포트다. aggregate 복원이 아니라 표시용 join 결과를 반환한다.
 */
public interface PerformanceGradeReadRepository {

    List<PerformanceGradeView> findAllByPerformanceIdOrderBySortOrderAsc(Long performanceId);
}
