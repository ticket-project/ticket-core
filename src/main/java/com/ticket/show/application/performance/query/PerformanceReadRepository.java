package com.ticket.show.application.performance.query;

import com.ticket.show.application.performance.query.model.PerformanceSummaryView;

import java.util.Optional;

public interface PerformanceReadRepository {

    Optional<PerformanceSummaryView> findByPerformanceId(Long performanceId);
}
