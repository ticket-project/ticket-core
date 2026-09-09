package com.ticket.show.application;

import com.ticket.show.application.PerformanceSummaryView;

import java.util.Optional;

public interface PerformanceReadRepository {

    Optional<PerformanceSummaryView> findByPerformanceId(Long performanceId);
}
