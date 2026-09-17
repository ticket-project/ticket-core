package com.ticket.show.query;

import org.jspecify.annotations.Nullable;

import com.ticket.shared.exception.InvalidRequestException;
import com.ticket.venue.api.Region;

import lombok.Getter;

/**
 * 공연 목록 조회 조건이다.
 *
 * <p>커서는 HTTP 문자열이 아니라 타입 값으로 받고, 지역 같은 도메인 enum 변환은 이 계층이 한다.
 */
@Getter
public class ShowListParam {
    private final @Nullable String category;
    private final @Nullable String genre;
    private final @Nullable Region region;
    private final @Nullable ShowCursor cursor;

    public ShowListParam(
            final @Nullable String category,
            final @Nullable String genre,
            final @Nullable Region region,
            final @Nullable ShowCursor cursor) {
        this.category = category;
        this.genre = genre;
        this.region = region;
        this.cursor = cursor;
    }

    public static ShowListParam of(
            final @Nullable String category,
            final @Nullable String genre,
            final @Nullable String region,
            final @Nullable ShowCursor cursor) {
        return new ShowListParam(category, genre, parseRegion(region), cursor);
    }

    /** 이전에는 Spring의 enum 변환기가 이 값을 바꿨고 그 변환기는 앞뒤 공백을 지웠다. 변환 주체가 이 계층으로 옮겨왔으므로 같은 관용을 유지한다. */
    static @Nullable Region parseRegion(final @Nullable String region) {
        if (region == null || region.isBlank()) {
            return null;
        }
        try {
            return Region.valueOf(region.trim());
        } catch (final IllegalArgumentException exception) {
            throw new InvalidRequestException("region 값이 올바르지 않습니다: " + region);
        }
    }
}
