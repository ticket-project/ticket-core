package com.ticket.show.domain;

import java.time.LocalDateTime;

/**
 * {@code PerformanceSaleCatalogService}가 조합하는 회차 표시값 중 show 부분이다. venue 표시값
 * ({@code venueName})은 여기 담지 않는다 — venue가 없는 show는 {@code venueId}가 {@code null}이다.
 */
public record PerformanceSaleContext(
        Long performanceId,
        Long showId,
        String showTitle,
        Long venueId,
        LocalDateTime performanceStartTime
) {
}
