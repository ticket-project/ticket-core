package com.ticket.show.application;

import com.ticket.venue.Region;

import java.time.LocalDateTime;

public record PerformanceSummaryView(
        String title,
        Region region,
        LocalDateTime startTime
) {
}
