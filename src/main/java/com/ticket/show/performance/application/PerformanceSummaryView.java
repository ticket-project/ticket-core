package com.ticket.show.performance.application;

import com.ticket.show.performance.application.usecase.GetPerformanceSummaryUseCase;

import java.time.LocalDateTime;

/**
 * {@code venueId}는 scalar 참조만 담는다 — region 표시값 조합(VenueLookup 호출)은
 * infrastructure가 아니라 {@code GetPerformanceSummaryUseCase}가 한다.
 */
public record PerformanceSummaryView(
        String title,
        Long venueId,
        LocalDateTime startTime
) {
}
