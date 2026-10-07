package com.ticket.show.usecase;

import java.util.Set;

import org.jspecify.annotations.Nullable;

import com.ticket.venue.api.VenueLookupApi;

/**
 * 공연 목록 조회 조건이다.
 *
 * <p>커서는 HTTP 문자열이 아니라 타입 값으로 받는다. 지역은 코드 문자열 그대로 들고 다닌다 — 코드가 실제 지역인지는 값 집합을 소유한 venue가 판정한다.
 */
public record ShowListParam(
        @Nullable String category,
        @Nullable String genre,
        @Nullable String region,
        @Nullable ShowCursor cursor) {
    public ShowListParam {
        region = normalizeRegion(region);
    }

    /**
     * 빈 문자열을 "필터 없음"으로 통일하고 앞뒤 공백을 지운다. 코드가 실제 지역인지는 보지 않는다 — venue가 판정한다.
     *
     * <p>목록·검색·오픈예정이 같은 규칙을 써야 하므로 여기 한 벌만 두고 각 조건 record의 compact 생성자가 부른다.
     */
    static @Nullable String normalizeRegion(final @Nullable String region) {
        if (region == null || region.isBlank()) {
            return null;
        }
        return region.trim();
    }

    /**
     * 정규화된 지역 코드를 공연장 ID 집합으로 바꾼다. {@code null}(필터 없음)과 공연장이 없는 지역의 빈 집합(결과 0건)을 구별해 돌려준다. 목록·검색·개수·오픈예정이 같은 규칙을 쓴다.
     */
    static @Nullable Set<Long> venueIdsIn(final VenueLookupApi venueLookupApi, final @Nullable String region) {
        return region == null ? null : venueLookupApi.findIdsByRegion(region);
    }
}
