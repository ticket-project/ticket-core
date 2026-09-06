package com.ticket.catalog.domain.performance.repository;

import com.ticket.catalog.domain.performance.PerformanceGrade;

import java.util.List;

/**
 * 회차별 Grade 연결(PerformanceGrade) aggregate의 복원을 담당하는 도메인 Repository다.
 *
 * <p>화면 조회는 여기 두지 않는다 — 회차별 grade 목록·가격 표시는 core-app의 read repository
 * ({@code PerformanceGradeReadRepository})가 담당한다.
 */
public interface PerformanceGradeRepository {

    List<PerformanceGrade> findAllByPerformanceIdOrderBySortOrderAsc(Long performanceId);

    boolean existsByPerformanceIdAndGradeId(Long performanceId, Long gradeId);
}
