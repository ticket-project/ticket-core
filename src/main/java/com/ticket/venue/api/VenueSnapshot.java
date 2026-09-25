package com.ticket.venue.api;

import java.math.BigDecimal;

import org.jspecify.annotations.Nullable;

/** 다른 module이 응답을 조합할 때 필요한 venue 표시값만 담은 불변 snapshot이다. JPA entity도 venue 도메인 enum도 노출하지 않는다. */
public record VenueSnapshot(
        long venueId,
        @Nullable String name,
        @Nullable String address,
        @Nullable RegionView region,
        @Nullable BigDecimal latitude,
        @Nullable BigDecimal longitude,
        @Nullable String phone,
        @Nullable String imageUrl,
        SeatMapLayout seatMapLayout) {
    /** 좌석 맵 SVG 렌더링에 필요한 배치값이다. {@code gapX}/{@code gapY}는 현재 소비자가 없어 담지 않는다(관련 GitHub Issue 참고). */
    public record SeatMapLayout(int viewBoxWidth, int viewBoxHeight, double seatDiameter) {}

    /**
     * 공연장 소재 지역의 코드({@code "SEOUL"})와 표시명({@code "서울"}) 쌍이다.
     *
     * <p>venue의 {@code Region} enum을 그대로 내보내지 않는다 — 값 집합은 venue가 소유하고, 호출하는 module은 둘 중 필요한 쪽을 골라 쓰기만 한다.
     */
    public record RegionView(String code, String name) {}
}
