package com.ticket.show.application.query;

import java.time.LocalDateTime;

public record PerformanceInfo(
        Long id, Long performanceNo, LocalDateTime startTime, LocalDateTime endTime) {}
