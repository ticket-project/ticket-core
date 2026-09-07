package com.ticket.show.domain.performance.query;

/**
 * {@code PerformanceVenueLayoutCatalog}이 조합하는 회차 표시값 중 show/venue 레이아웃 부분이다.
 * venue가 없는 show는 {@code venueId}/{@code venueName}과 레이아웃 값이 {@code null}/기본값이다.
 */
public record PerformanceVenueLayoutContext(
        Long performanceId,
        Long venueId,
        String venueName,
        Integer viewBoxWidth,
        Integer viewBoxHeight,
        Double seatDiameter
) {
}
