package com.ticket.catalog.application.performance.query.model;

import com.ticket.catalog.domain.show.Region;

import java.time.LocalDateTime;

public record PerformanceSummaryView(
        String title,
        Region region,
        LocalDateTime startTime,
        Integer maxCanHoldCount
) {
}
