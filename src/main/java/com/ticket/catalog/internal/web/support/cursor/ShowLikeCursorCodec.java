package com.ticket.core.api.support.cursor;

import com.ticket.error.InvalidRequestException;
import org.springframework.stereotype.Component;

/**
 * 찜 목록 커서의 wire 표현을 담당한다.
 *
 * <p>기존 계약을 유지하기 위해 마지막 찜 id를 그대로 십진수 문자열로 쓴다.
 */
@Component
public class ShowLikeCursorCodec {

    public String encode(final Long lastLikeId) {
        return lastLikeId == null ? null : String.valueOf(lastLikeId);
    }

    public Long decode(final String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(cursor.trim());
        } catch (final NumberFormatException exception) {
            throw new InvalidRequestException("cursor 형식이 올바르지 않습니다.");
        }
    }
}
