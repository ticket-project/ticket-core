package com.ticket.show;

/**
 * 다른 module이 좌석 맵 SVG를 그릴 때 쓰는 공연장 배치 표시값이다. {@code Venue} JPA entity를
 * 노출하지 않는다.
 */
public record VenueLayout(
        String name,
        int viewBoxWidth,
        int viewBoxHeight,
        double seatDiameter
) {
}
