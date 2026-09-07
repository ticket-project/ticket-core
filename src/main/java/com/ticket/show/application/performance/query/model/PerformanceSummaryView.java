package com.ticket.show.application.performance.query.model;

import com.ticket.show.domain.show.Region;

import java.time.LocalDateTime;

public record PerformanceSummaryView(
        String title,
        Region region,
        LocalDateTime startTime,
        Integer maxCanHoldCount
) {
}
