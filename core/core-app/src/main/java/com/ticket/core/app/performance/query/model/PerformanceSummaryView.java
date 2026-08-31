package com.ticket.core.app.performance.query.model;

import com.ticket.core.domain.show.model.Region;

import java.time.LocalDateTime;

public record PerformanceSummaryView(
        String title,
        Region region,
        LocalDateTime startTime,
        Integer maxCanHoldCount
) {
}
