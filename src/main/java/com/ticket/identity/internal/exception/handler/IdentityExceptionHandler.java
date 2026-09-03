package com.ticket.identity.internal.exception.handler;

import com.ticket.identity.internal.exception.IdentityException;
import com.ticket.shared.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * identity 오류를 응답으로 옮긴다.
 *
 * <p>Spring Security filter chain에서 나는 인증·인가 실패는 이 handler를 거치지 않는다 —
 * {@code RestAuthenticationEntryPoint}/{@code RestAccessDeniedHandler}가 message converter 없이
 * 직접 직렬화한다. 두 경로가 같은 봉투를 내는지는 각 handler의 테스트가 고정한다.
 *
 * <p>base 예외 하나만 잡는다 — 그 범위는 {@code com.ticket.error.ExceptionHandlerScopeTest}가 강제한다.
 */
@Slf4j
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class IdentityExceptionHandler {

    @ExceptionHandler(IdentityException.class)
    public ResponseEntity<ApiResponse<Object>> handleIdentityException(final IdentityException exception) {
        log.info("identity.rejected: code={}", exception.getErrorCode().getCode());

        return ResponseEntity
                .status(exception.getStatus())
                .body(ApiResponse.error(
                        exception.getErrorCode().getCode(), exception.getMessage(), exception.getData()));
    }
}
