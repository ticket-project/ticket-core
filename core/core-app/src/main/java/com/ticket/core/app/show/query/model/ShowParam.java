package com.ticket.core.app.show.query.model;

import com.ticket.core.app.error.ApplicationErrorType;
import com.ticket.core.domain.show.meta.Region;
import com.ticket.support.error.CoreException;
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

    static Region parseRegion(final String region) {
        if (region == null || region.isBlank()) {
            return null;
        }
        try {
            return Region.valueOf(region);
        } catch (final IllegalArgumentException exception) {
            throw new CoreException(ApplicationErrorType.INVALID_INPUT, "region 값이 올바르지 않습니다: " + region);
        }
    }
}
