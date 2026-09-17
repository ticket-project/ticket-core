package com.ticket.booking.infrastructure.persistence;

import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.ticket.booking.domain.salespolicy.PerformanceSalesPolicy;
import com.ticket.booking.domain.salespolicy.PerformanceSalesPolicyRepository;

import lombok.RequiredArgsConstructor;

/** {@link PerformanceSalesPolicyRepository}의 JPA 구현이다. */
@Repository
@RequiredArgsConstructor
public class PerformanceSalesPolicyRepositoryAdapter implements PerformanceSalesPolicyRepository {
    private final SpringDataPerformanceSalesPolicyJpaRepository jpaRepository;

    @Override
    public Optional<PerformanceSalesPolicy> findById(final Long performanceId) {
        return jpaRepository.findById(performanceId);
    }
}
