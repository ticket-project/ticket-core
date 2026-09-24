package com.ticket.security.exception.handler;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.ticket.security.exception.AuthorizationException;
import com.ticket.security.exception.UnauthenticatedException;
import com.ticket.shared.web.ApiResponse;

@SuppressWarnings("NonAsciiCharacters")
class SecurityExceptionHandlerTest {
    private final SecurityExceptionHandler handler = new SecurityExceptionHandler();

    @Test
    void 인증_실패는_기존_상태와_코드로_응답한다() {
        final ResponseEntity<ApiResponse<Object>> response =
                handler.handleUnauthenticated(new UnauthenticatedException("토큰 오류"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getError().getCode()).isEqualTo("E1000");
        assertThat(response.getBody().getError().getMessage()).isEqualTo("로그인이 필요합니다.");
        assertThat(response.getBody().getError().getData()).isEqualTo("토큰 오류");
    }

    @Test
    void 인가_실패는_기존_상태와_코드로_응답한다() {
        final ResponseEntity<ApiResponse<Object>> response =
                handler.handleAuthorization(new AuthorizationException("권한 오류"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getError().getCode()).isEqualTo("E1001");
        assertThat(response.getBody().getError().getMessage()).isEqualTo("권한이 없습니다.");
        assertThat(response.getBody().getError().getData()).isEqualTo("권한 오류");
    }
}
