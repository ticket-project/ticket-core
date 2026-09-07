package com.ticket.venue;

import java.math.BigDecimal;

/**
 * 다른 module이 응답을 조합할 때 필요한 venue 표시값만 담은 불변 snapshot이다. JPA entity를
 * 노출하지 않는다.
 */
public record VenueSummary(
        long venueId,
        String name,
        String address,
        Region region,
        BigDecimal latitude,
        BigDecimal longitude,
        String phone,
        String imageUrl,
        SeatMapLayout seatMapLayout
) {
    /**
     * 좌석 맵 SVG 렌더링에 필요한 배치값이다. {@code gapX}/{@code gapY}는 현재 소비자가 없어
     * 담지 않는다(technical-debt 참고).
     */
    public record SeatMapLayout(int viewBoxWidth, int viewBoxHeight, double seatDiameter) {
    }
}
