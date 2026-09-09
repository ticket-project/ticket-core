package com.ticket.show.application;

import java.time.LocalDateTime;

public record PerformanceInfo(
        Long id,
        Long performanceNo,
        LocalDateTime startTime,
        LocalDateTime endTime
) {
}
