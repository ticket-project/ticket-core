package com.ticket.show.api;

import java.time.LocalDateTime;

import org.jspecify.annotations.Nullable;

/** 회차 요약이다. {@code GET /api/v1/performances/{performanceId}/summary} 응답과 같은 값이다. */
public record PerformanceSummarySnapshot(
        @Nullable String title,
        @Nullable String region,
        @Nullable LocalDateTime startTime) {}
