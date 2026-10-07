package com.ticket.security.exception.handler;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.ticket.security.exception.AuthException;
import com.ticket.security.exception.SecurityErrorCode;
import com.ticket.shared.web.ApiResponse;

/**
 * MVC 경계에서 발생한 인증·인가 오류를 기존 응답 형식으로 옮긴다. {@link AuthException}은 상태를 모른다 — 오류 코드별 HTTP 상태는 이 handler가 안다. filter chain에서
 * 나는 인증 실패는 이 handler를 거치지 않고 {@code RestAuthenticationEntryPoint}가 같은 401을 직접 쓴다.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SecurityExceptionHandler {
    @ExceptionHandler(AuthException.class)
    public ResponseEntity<ApiResponse<Object>> handleSecurityException(final AuthException exception) {
        return ResponseEntity.status(statusOf(exception.getErrorCode()))
                .body(ApiResponse.error(
                        exception.getErrorCode().getCode(), exception.getMessage(), exception.getData()));
    }

    private HttpStatus statusOf(final SecurityErrorCode errorCode) {
        return switch (errorCode) {
            case E1000 -> HttpStatus.UNAUTHORIZED;
            case E1001 -> HttpStatus.FORBIDDEN;
        };
    }
}
