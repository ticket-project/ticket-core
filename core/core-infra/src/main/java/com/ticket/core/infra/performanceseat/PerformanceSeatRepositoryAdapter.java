package com.ticket.core.infra.performanceseat;

import com.ticket.core.domain.performanceseat.model.PerformanceSeat;
import com.ticket.core.domain.performanceseat.model.PerformanceSeatState;
import com.ticket.core.domain.performanceseat.repository.PerformanceSeatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

/**
 * {@link PerformanceSeatRepository}의 JPA 구현이다.
 */
@Repository
@RequiredArgsConstructor
public class PerformanceSeatRepositoryAdapter implements PerformanceSeatRepository {

    private final SpringDataPerformanceSeatJpaRepository jpaRepository;

    @Override
    public List<PerformanceSeat> findAllByPerformanceIdAndSeatIdIn(
            final Long performanceId,
            final Collection<Long> seatIds
    ) {
        return jpaRepository.findAllByPerformanceIdAndSeatIdIn(performanceId, seatIds);
    }

    @Override
    public List<PerformanceSeat> findAllByStateEquals(final PerformanceSeatState state) {
        return jpaRepository.findAllByStateEquals(state);
    }
}
