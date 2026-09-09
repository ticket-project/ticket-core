package com.ticket.member.infrastructure;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 인증 실패 응답 계약을 고정한다.
 *
 * <p>이 핸들러는 Spring MVC 메시지 컨버터를 거치지 않고 {@code response.getWriter()}에 직접 쓰므로
 * {@code GlobalExceptionHandler}를 검증하는 테스트가 이 경로를 대신 지켜주지 못한다. 특히 {@code jwt.error}
 * request attribute로 갈라지는 문구가 {@code error.message}가 아니라 <b>{@code error.data}</b>에 실린다는
 * 점이 계약이다 — {@code error.message}에는 {@code UnauthenticatedException}의 고정 문구가 들어간다.
 */
@SuppressWarnings("NonAsciiCharacters")
class RestAuthenticationEntryPointTest {

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    private final RestAuthenticationEntryPoint entryPoint = new RestAuthenticationEntryPoint(JSON_MAPPER);

    @Test
    void 인증_실패는_401과_E1000_봉투로_응답한다() throws Exception {
        final MockHttpServletResponse response = commence(null);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).isEqualTo("application/json;charset=UTF-8");
        assertThat(response.getCharacterEncoding()).isEqualToIgnoringCase("UTF-8");

        final JsonNode body = body(response);
        assertThat(body.get("result").asString()).isEqualTo("ERROR");
        assertThat(body.get("data").isNull()).isTrue();
        assertThat(body.get("error").get("code").asString()).isEqualTo("E1000");
        assertThat(body.get("error").get("message").asString()).isEqualTo("로그인이 필요합니다.");
    }

    @Test
    void jwt_error가_없으면_error_data에_기본_문구가_실린다() throws Exception {
        assertThat(errorData(commence(null))).isEqualTo("로그인이 필요합니다.");
    }

    @Test
    void 만료된_토큰은_error_data에_재로그인_안내를_싣는다() throws Exception {
        assertThat(errorData(commence("expired"))).isEqualTo("토큰이 만료되었습니다. 다시 로그인해주세요.");
    }

    @Test
    void 유효하지_않은_토큰은_error_data에_해당_문구를_싣는다() throws Exception {
        assertThat(errorData(commence("invalid"))).isEqualTo("유효하지 않은 토큰입니다.");
    }

    @Test
    void 알_수_없는_jwt_error_값은_기본_문구로_떨어진다() throws Exception {
        assertThat(errorData(commence("something-else"))).isEqualTo("로그인이 필요합니다.");
    }

    private MockHttpServletResponse commence(final String jwtError) throws Exception {
        final MockHttpServletRequest request = new MockHttpServletRequest();
        if (jwtError != null) {
            request.setAttribute("jwt.error", jwtError);
        }
        final MockHttpServletResponse response = new MockHttpServletResponse();

        entryPoint.commence(request, response, new TestAuthenticationException());

        return response;
    }

    private String errorData(final MockHttpServletResponse response) {
        return body(response).get("error").get("data").asString();
    }

    private JsonNode body(final MockHttpServletResponse response) {
        try {
            return JSON_MAPPER.readTree(response.getContentAsString());
        } catch (final Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static final class TestAuthenticationException extends AuthenticationException {
        private TestAuthenticationException() {
            super("authentication required");
        }
    }
}
