package com.ticket.core.app.support.cursor;

import tools.jackson.databind.json.JsonMapper;
import com.ticket.core.app.show.query.model.ShowCursor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Component
@RequiredArgsConstructor
public class CursorCodec {

    private final JsonMapper jsonMapper;

    public String encode(ShowCursor cursor) {
        try {
            String json = jsonMapper.writeValueAsString(cursor);
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(json.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalArgumentException("cursor encode failed", e);
        }
    }

    public ShowCursor decode(String token) {
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(token);
            String json = new String(decoded, StandardCharsets.UTF_8);
            return jsonMapper.readValue(json, ShowCursor.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("cursor decode failed", e);
        }
    }
}
