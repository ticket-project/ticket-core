package com.ticket.core.api.support.cursor;

import com.ticket.core.api.error.ApiErrorType;
import com.ticket.core.app.show.query.model.ShowCursor;
import com.ticket.support.error.CoreException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 공연 목록 커서의 wire 표현을 담당한다.
 *
 * <p>커서 문자열은 HTTP 계약이므로 core-api가 소유한다. core-app과 core-infra는 타입 값인
 * {@link ShowCursor}만 주고받는다. 인코딩 형식은 기존과 같은 URL-safe Base64(JSON)이다.
 */
@Component
@RequiredArgsConstructor
public class ShowCursorCodec {

    private final JsonMapper jsonMapper;

    public String encode(final ShowCursor cursor) {
        if (cursor == null) {
            return null;
        }
        try {
            final String json = jsonMapper.writeValueAsString(cursor);
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(json.getBytes(StandardCharsets.UTF_8));
        } catch (final Exception e) {
            throw new CoreException(ApiErrorType.INVALID_REQUEST, "cursor 형식이 올바르지 않습니다.");
        }
    }

    public ShowCursor decode(final String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            final byte[] decoded = Base64.getUrlDecoder().decode(token);
            final String json = new String(decoded, StandardCharsets.UTF_8);
            return jsonMapper.readValue(json, ShowCursor.class);
        } catch (final Exception e) {
            throw new CoreException(ApiErrorType.INVALID_REQUEST, "cursor 형식이 올바르지 않습니다.");
        }
    }
}
