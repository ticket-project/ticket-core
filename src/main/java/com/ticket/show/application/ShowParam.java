package com.ticket.show.application;

import com.ticket.venue.Region;
import com.ticket.error.InvalidRequestException;
import lombok.Getter;

/**
 * 공연 목록 조회 조건이다.
 *
 * <p>커서는 HTTP 문자열이 아니라 타입 값으로 받고, 지역 같은 도메인 enum 변환은 이 계층이 한다.
 */
@Getter
public class ShowParam {

    private final String category;

    private final String genre;

    private final Region region;

    private final ShowCursor cursor;

    public ShowParam(final String category, final String genre, final Region region, final ShowCursor cursor) {
        this.category = category;
        this.genre = genre;
        this.region = region;
        this.cursor = cursor;
    }

    public static ShowParam of(
            final String category,
            final String genre,
            final String region,
            final ShowCursor cursor
    ) {
        return new ShowParam(category, genre, parseRegion(region), cursor);
    }

    /**
     * 이전에는 Spring의 enum 변환기가 이 값을 바꿨고 그 변환기는 앞뒤 공백을 지웠다.
     * 변환 주체가 이 계층으로 옮겨왔으므로 같은 관용을 유지한다.
     */
    static Region parseRegion(final String region) {
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
