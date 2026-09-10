package com.ticket.member.exception.handler;

import com.ticket.member.exception.AuthorizationException;
import com.ticket.member.exception.DuplicateEmailException;
import com.ticket.member.exception.MemberException;
import com.ticket.member.exception.UnauthenticatedException;
import com.ticket.shared.web.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * member 오류를 응답으로 옮긴다.
 *
 * <p>Spring Security filter chain에서 나는 인증·인가 실패는 이 handler를 거치지 않는다 —
 * {@code RestAuthenticationEntryPoint}/{@code RestAccessDeniedHandler}가 message converter 없이
 * 직접 직렬화한다. 두 경로가 같은 봉투를 내는지는 각 handler의 테스트가 고정한다.
 *
 * <p>base 예외 하나만 잡는다 — 그 범위는 {@code com.ticket.shared.exception.ExceptionHandlerScopeTest}가 강제한다.
 * {@link MemberException}은 상태를 모른다 — 구체 타입별 HTTP 상태는 이 handler가 안다.
 */
@Slf4j
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MemberExceptionHandler {

    @ExceptionHandler(MemberException.class)
    public ResponseEntity<ApiResponse<Object>> handleMemberException(final MemberException exception) {
        log.info("member.rejected: code={}", exception.getErrorCode().getCode());

        return ResponseEntity
                .status(statusOf(exception))
                .body(ApiResponse.error(
                        exception.getErrorCode().getCode(), exception.getMessage(), exception.getData()));
    }

    private HttpStatus statusOf(final MemberException exception) {
        return switch (exception) {
            case UnauthenticatedException e -> HttpStatus.UNAUTHORIZED;
            case AuthorizationException e -> HttpStatus.FORBIDDEN;
            case DuplicateEmailException e -> HttpStatus.CONFLICT;
            default -> throw new IllegalStateException(
                    "알 수 없는 MemberException 하위 타입입니다: " + exception.getClass().getName());
        };
    }
}
