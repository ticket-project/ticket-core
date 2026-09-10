package com.ticket.show.performance.application.port;

import com.ticket.show.performance.application.PerformanceSummaryView;

import java.util.Optional;

public interface PerformanceQueryPort {

    Optional<PerformanceSummaryView> findByPerformanceId(Long performanceId);
}
