package com.ticket.catalog.internal.infrastructure.performance;

import com.ticket.catalog.internal.domain.performance.PerformanceGrade;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface SpringDataPerformanceGradeJpaRepository extends JpaRepository<PerformanceGrade, Long> {

    List<PerformanceGrade> findAllByPerformance_IdOrderBySortOrderAsc(Long performanceId);

    boolean existsByPerformance_IdAndGrade_Id(Long performanceId, Long gradeId);
}
