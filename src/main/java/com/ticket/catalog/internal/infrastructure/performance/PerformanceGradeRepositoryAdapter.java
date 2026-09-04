package com.ticket.catalog.internal.infrastructure.performance;

import com.ticket.catalog.internal.domain.performance.PerformanceGrade;
import com.ticket.catalog.internal.domain.performance.repository.PerformanceGradeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * {@link PerformanceGradeRepository}의 JPA 구현이다.
 */
@Repository
@RequiredArgsConstructor
public class PerformanceGradeRepositoryAdapter implements PerformanceGradeRepository {

    private final SpringDataPerformanceGradeJpaRepository jpaRepository;

    @Override
    public List<PerformanceGrade> findAllByPerformanceIdOrderBySortOrderAsc(final Long performanceId) {
        return jpaRepository.findAllByPerformance_IdOrderBySortOrderAsc(performanceId);
    }

    @Override
    public boolean existsByPerformanceIdAndGradeId(final Long performanceId, final Long gradeId) {
        return jpaRepository.existsByPerformance_IdAndGrade_Id(performanceId, gradeId);
    }
}
