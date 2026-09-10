package com.ticket.show.performance.domain;

/**
 * {@code PerformanceVenueLayoutCatalogService}가 조합하는 회차 표시값 중 show 부분이다. venue
 * 표시값(이름·seat-map 좌표)은 여기 담지 않는다 — venue가 없는 show는 {@code venueId}가
 * {@code null}이다.
 */
public record PerformanceVenueLayoutContext(
        Long performanceId,
        Long venueId
) {
}
