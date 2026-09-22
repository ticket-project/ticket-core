package com.ticket.show.usecase;

import org.jspecify.annotations.Nullable;

import lombok.Getter;

/**
 * 공연 목록 조회 조건이다.
 *
 * <p>커서는 HTTP 문자열이 아니라 타입 값으로 받는다. 지역은 코드 문자열 그대로 들고 다닌다 — 코드가 실제 지역인지는 값 집합을 소유한 venue가 판정한다.
 */
@Getter
public class ShowListParam {
    private final @Nullable String category;
    private final @Nullable String genre;
    private final @Nullable String region;
    private final @Nullable ShowCursor cursor;

    public ShowListParam(
            final @Nullable String category,
            final @Nullable String genre,
            final @Nullable String region,
            final @Nullable ShowCursor cursor) {
        this.category = category;
        this.genre = genre;
        this.region = normalizeRegion(region);
        this.cursor = cursor;
    }

    public static ShowListParam of(
            final @Nullable String category,
            final @Nullable String genre,
            final @Nullable String region,
            final @Nullable ShowCursor cursor) {
        return new ShowListParam(category, genre, region, cursor);
    }

    /**
     * 빈 문자열을 "필터 없음"으로 통일하고 앞뒤 공백을 지운다. 코드가 실제 지역인지는 보지 않는다 — venue가 판정한다.
     *
     * <p>목록·검색·오픈예정이 같은 규칙을 써야 하므로 여기 한 벌만 둔다. 부르는 것은 <b>각 조건 객체의 생성자</b>다 — 정규화가 {@code of()}에만 있으면 생성자로 만든 객체와 뜻이
     * 달라진다.
     */
    static @Nullable String normalizeRegion(final @Nullable String region) {
        if (region == null || region.isBlank()) {
            return null;
        }
        return region.trim();
    }
}
