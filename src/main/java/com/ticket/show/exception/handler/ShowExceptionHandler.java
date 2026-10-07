package com.ticket.show.exception.handler;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.ticket.shared.web.ApiResponse;
import com.ticket.show.exception.ShowErrorCode;
import com.ticket.show.exception.ShowException;

import lombok.extern.slf4j.Slf4j;

/**
 * show 오류를 응답으로 옮긴다. 자기 module 예외만 잡는다 — 그 범위는 {@code com.ticket.shared.exception.ExceptionHandlerScopeTest}가 강제한다.
 *
 * <p>{@link ShowException}은 상태를 모른다 — 오류 코드별 HTTP 상태는 이 handler가 안다.
 */
@Slf4j
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ShowExceptionHandler {
    @ExceptionHandler(ShowException.class)
    public ResponseEntity<ApiResponse<Object>> handleShowException(final ShowException exception) {
        log.info("show.rejected: code={}", exception.getErrorCode().getCode());

        return ResponseEntity.status(statusOf(exception.getErrorCode()))
                .body(ApiResponse.error(
                        exception.getErrorCode().getCode(), exception.getMessage(), exception.getData()));
    }

    private HttpStatus statusOf(final ShowErrorCode errorCode) {
        return switch (errorCode) {
            case E7002 -> HttpStatus.BAD_REQUEST;
        };
    }
}
