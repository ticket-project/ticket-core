package com.ticket.booking.salespolicy.persistence;

import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.ticket.booking.salespolicy.domain.PerformanceSalesPolicy;
import com.ticket.booking.salespolicy.domain.PerformanceSalesPolicyRepository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class PerformanceSalesPolicyRepositoryAdapter implements PerformanceSalesPolicyRepository {
    private final SpringDataPerformanceSalesPolicyJpaRepository jpaRepository;

    @Override
    public Optional<PerformanceSalesPolicy> findById(final Long performanceId) {
        return jpaRepository.findById(performanceId);
    }
}
