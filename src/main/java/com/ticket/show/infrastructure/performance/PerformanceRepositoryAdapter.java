package com.ticket.show.infrastructure.performance;

import com.ticket.show.domain.performance.Performance;
import com.ticket.show.domain.performance.repository.PerformanceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * {@link PerformanceRepository}의 JPA 구현이다.
 */
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
}
