package com.ticket.core.domain.performance.query.model;

import com.ticket.core.domain.show.meta.Region;

import java.time.LocalDateTime;

public record PerformanceSummaryView(
        String title,
        Region region,
        LocalDateTime startTime,
        Integer maxCanHoldCount
) {
}
