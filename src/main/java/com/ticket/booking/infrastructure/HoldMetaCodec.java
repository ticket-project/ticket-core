package com.ticket.booking.infrastructure;

import com.ticket.booking.domain.Hold;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * hold 메타를 Redis에 저장하기 위한 기술 직렬화다.
 *
 * <p>직렬화 형식은 저장 기술의 관심사이므로 이 클래스가 속한 {@code booking.infrastructure.hold}가
 * 소유한다.
 */
@Component
@RequiredArgsConstructor
class HoldMetaCodec {

    private final JsonMapper jsonMapper;

    String encode(final Hold hold) {
        try {
            return jsonMapper.writeValueAsString(hold);
        } catch (final Exception e) {
            throw new IllegalStateException("hold meta encode failed", e);
        }
    }

    Hold decode(final String payload) {
        try {
            return jsonMapper.readValue(payload, Hold.class);
        } catch (final Exception e) {
            throw new IllegalStateException("hold meta decode failed", e);
        }
    }
}
