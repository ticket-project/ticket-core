package com.ticket.core.domain.performance.query;

import com.ticket.core.domain.performance.query.model.PerformanceBookingPolicyView;

import java.util.Optional;

public interface PerformanceBookingPolicyQueryRepository {

    Optional<PerformanceBookingPolicyView> findByPerformanceId(Long performanceId);
}
