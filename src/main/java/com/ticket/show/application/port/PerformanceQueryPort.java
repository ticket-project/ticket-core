package com.ticket.show.application.port;

import com.ticket.show.application.PerformanceSummaryView;

import java.util.Optional;

public interface PerformanceQueryPort {

    Optional<PerformanceSummaryView> findByPerformanceId(Long performanceId);
}
