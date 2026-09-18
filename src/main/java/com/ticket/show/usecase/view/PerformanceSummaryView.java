package com.ticket.show.usecase.view;

import java.time.LocalDateTime;

import org.jspecify.annotations.Nullable;

/**
 * {@code venueId}는 scalar 참조만 담는다 — region 표시값 조합(VenueLookupApi 호출)은 infrastructure가 아니라 {@code
 * GetPerformanceSummaryUseCase}가 한다.
 */
public record PerformanceSummaryView(
        @Nullable String title, @Nullable Long venueId, @Nullable LocalDateTime startTime) {}
