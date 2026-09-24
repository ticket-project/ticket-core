package com.ticket.security.exception.handler;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.ticket.security.exception.AuthorizationException;
import com.ticket.security.exception.UnauthenticatedException;
import com.ticket.shared.exception.TicketException;
import com.ticket.shared.web.ApiResponse;

/** MVC 경계에서 발생한 인증·인가 오류를 기존 응답 형식으로 옮긴다. */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SecurityExceptionHandler {
    @ExceptionHandler(UnauthenticatedException.class)
    public ResponseEntity<ApiResponse<Object>> handleUnauthenticated(final UnauthenticatedException exception) {
        return response(HttpStatus.UNAUTHORIZED, exception);
    }

    @ExceptionHandler(AuthorizationException.class)
    public ResponseEntity<ApiResponse<Object>> handleAuthorization(final AuthorizationException exception) {
        return response(HttpStatus.FORBIDDEN, exception);
    }

    private ResponseEntity<ApiResponse<Object>> response(final HttpStatus status, final TicketException exception) {
        return ResponseEntity.status(status)
                .body(ApiResponse.error(
                        exception.getErrorCode().getCode(), exception.getMessage(), exception.getData()));
    }
}
