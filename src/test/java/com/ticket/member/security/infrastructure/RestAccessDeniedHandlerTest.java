package com.ticket.member.security.infrastructure;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 인가 실패 응답 계약을 고정한다.
 *
 * <p>{@link RestAuthenticationEntryPointTest}와 같은 이유로 필요하다 — 이 핸들러도 메시지 컨버터를
 * 거치지 않고 직접 직렬화한다. 인증 실패와 달리 {@code error.data}는 항상 null이다.
 */
@SuppressWarnings("NonAsciiCharacters")
class RestAccessDeniedHandlerTest {

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    private final RestAccessDeniedHandler handler = new RestAccessDeniedHandler(JSON_MAPPER);

    @Test
    void 인가_실패는_403과_E1001_봉투로_응답한다() throws Exception {
        final MockHttpServletResponse response = handle();

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).isEqualTo("application/json;charset=UTF-8");
        assertThat(response.getCharacterEncoding()).isEqualToIgnoringCase("UTF-8");

        final JsonNode body = body(response);
        assertThat(body.get("result").asString()).isEqualTo("ERROR");
        assertThat(body.get("data").isNull()).isTrue();
        assertThat(body.get("error").get("code").asString()).isEqualTo("E1001");
        assertThat(body.get("error").get("message").asString()).isEqualTo("권한이 없습니다.");
    }

    @Test
    void 인가_실패는_error_data를_비워_둔다() throws Exception {
        assertThat(body(handle()).get("error").get("data").isNull()).isTrue();
    }

    private MockHttpServletResponse handle() throws Exception {
        final MockHttpServletRequest request = new MockHttpServletRequest();
        final MockHttpServletResponse response = new MockHttpServletResponse();

        handler.handle(request, response, new AccessDeniedException("access denied"));

        return response;
    }

    private JsonNode body(final MockHttpServletResponse response) {
        try {
            return JSON_MAPPER.readTree(response.getContentAsString());
        } catch (final Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
