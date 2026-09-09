package com.ticket.booking.infrastructure;

import com.ticket.booking.domain.PerformanceSalesPolicy;
import com.ticket.booking.domain.PerformanceSalesPolicyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * {@link PerformanceSalesPolicyRepository}의 JPA 구현이다.
 */
@Repository
@RequiredArgsConstructor
public class PerformanceSalesPolicyRepositoryAdapter implements PerformanceSalesPolicyRepository {

    private final SpringDataPerformanceSalesPolicyJpaRepository jpaRepository;

    @Override
    public Optional<PerformanceSalesPolicy> findById(final Long performanceId) {
        return jpaRepository.findById(performanceId);
    }
}
