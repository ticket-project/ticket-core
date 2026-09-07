package com.ticket.show.domain.performance.query;

import java.time.LocalDateTime;

/**
 * {@code PerformanceSaleCatalog}이 조합하는 회차 표시값 중 show/venue 부분이다. venue가 없는 show는
 * {@code venueId}/{@code venueName}이 {@code null}이다.
 */
public record PerformanceSaleContext(
        Long performanceId,
        Long showId,
        String showTitle,
        Long venueId,
        String venueName,
        LocalDateTime performanceStartTime
) {
}
