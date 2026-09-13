package com.ticket.show.performance.application.port;

import java.util.Optional;

import com.ticket.show.performance.application.PerformanceSummaryView;

public interface PerformanceQueryPort {
    Optional<PerformanceSummaryView> findByPerformanceId(Long performanceId);
}
