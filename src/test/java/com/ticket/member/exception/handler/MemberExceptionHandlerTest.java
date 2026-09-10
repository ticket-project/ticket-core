package com.ticket.member.exception.handler;

import com.ticket.member.exception.AuthorizationException;
import com.ticket.member.exception.DuplicateEmailException;
import com.ticket.member.exception.MemberException;
import com.ticket.member.exception.UnauthenticatedException;
import com.ticket.shared.web.ApiResponse;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * member 오류의 외부 계약(HTTP 상태, E-code, 공개 메시지)을 한곳에 고정한다.
 *
 * <p>인증·인가 실패 중 Spring Security filter chain에서 나는 것은 이 handler를 거치지 않고
 * {@code RestAuthenticationEntryPoint}/{@code RestAccessDeniedHandler}가 처리한다 — 그 경로는
 * 각자의 테스트가 고정한다. 이 handler는 filter 통과 후 use case 계층에서 던지는 것만 다룬다.
 */
@SuppressWarnings("NonAsciiCharacters")
class MemberExceptionHandlerTest {

    private final MemberExceptionHandler handler = new MemberExceptionHandler();

    static Stream<Arguments> 오류_계약() {
        return Stream.of(
                Arguments.of(new UnauthenticatedException(), HttpStatus.UNAUTHORIZED, "E1000",
                        "로그인이 필요합니다."),
                Arguments.of(new AuthorizationException(), HttpStatus.FORBIDDEN, "E1001",
                        "권한이 없습니다."),
                Arguments.of(new DuplicateEmailException(), HttpStatus.CONFLICT, "E2000",
                        "중복된 이메일은 불가능합니다."));
    }

    @ParameterizedTest
    @MethodSource("오류_계약")
    void member_오류는_정해진_상태와_E_code와_메시지로_응답한다(
            final MemberException exception,
            final HttpStatus expectedStatus,
            final String expectedCode,
            final String expectedMessage
    ) {
        final ResponseEntity<ApiResponse<Object>> response = handler.handleMemberException(exception);

        assertThat(response.getStatusCode()).isEqualTo(expectedStatus);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getError().getCode()).isEqualTo(expectedCode);
        assertThat(response.getBody().getError().getMessage()).isEqualTo(expectedMessage);
        assertThat(response.getBody().getData()).isNull();
    }
}
