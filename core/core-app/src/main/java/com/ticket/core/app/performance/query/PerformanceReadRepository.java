package com.ticket.core.app.performance.query;

import com.ticket.core.app.performance.query.model.PerformanceSummaryView;

import java.util.Optional;

public interface PerformanceReadRepository {

    Optional<PerformanceSummaryView> findByPerformanceId(Long performanceId);
}
