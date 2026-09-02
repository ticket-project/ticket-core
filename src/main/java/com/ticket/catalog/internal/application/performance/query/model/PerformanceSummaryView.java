package com.ticket.catalog.internal.application.performance.query.model;

import com.ticket.catalog.internal.domain.show.Region;

import java.time.LocalDateTime;

public record PerformanceSummaryView(
        String title,
        Region region,
        LocalDateTime startTime,
        Integer maxCanHoldCount
) {
}
