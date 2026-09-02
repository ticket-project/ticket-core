package com.ticket.catalog.internal.application.performance.query;

import com.ticket.catalog.internal.application.performance.query.model.PerformanceSummaryView;

import java.util.Optional;

public interface PerformanceReadRepository {

    Optional<PerformanceSummaryView> findByPerformanceId(Long performanceId);
}
