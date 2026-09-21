package com.ticket.show.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.ticket.show.domain.performance.Performance;
import com.ticket.show.domain.performance.PerformanceGrade;
import com.ticket.show.domain.performance.PerformanceRepository;

import lombok.RequiredArgsConstructor;

/** {@link PerformanceRepository}의 JPA 구현이다. */
@Repository
@RequiredArgsConstructor
public class PerformanceRepositoryAdapter implements PerformanceRepository {
    private final SpringDataPerformanceJpaRepository jpaRepository;

    @Override
    public List<Performance> findAllByShowIdOrderByStartTimeAscPerformanceNoAsc(final Long showId) {
        return jpaRepository.findAllByShowIdOrderByStartTimeAscPerformanceNoAsc(showId);
    }

    @Override
    public Optional<Performance> findById(final Long performanceId) {
        return jpaRepository.findById(performanceId);
    }

    @Override
    public Optional<Long> findRepresentativePerformanceIdByShowId(final long showId) {
        return jpaRepository.findRepresentativePerformanceIdByShowId(showId);
    }

    @Override
    public List<PerformanceGrade> findPerformanceGrades(final long performanceId) {
        return jpaRepository.findGradesByPerformanceId(performanceId);
    }
}
