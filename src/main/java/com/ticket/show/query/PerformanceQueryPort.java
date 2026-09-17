package com.ticket.show.query;

import java.util.Optional;

public interface PerformanceQueryPort {
    Optional<PerformanceSummaryView> findByPerformanceId(Long performanceId);
}
