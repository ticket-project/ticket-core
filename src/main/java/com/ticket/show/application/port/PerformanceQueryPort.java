package com.ticket.show.application.port;

import java.util.Optional;

import com.ticket.show.application.PerformanceSummaryView;

public interface PerformanceQueryPort {
    Optional<PerformanceSummaryView> findByPerformanceId(Long performanceId);
}
