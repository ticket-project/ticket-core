package com.ticket.showlike.internal.exception.handler;

import com.ticket.shared.ApiResponse;
import com.ticket.showlike.internal.exception.ShowLikeException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * showlike 오류를 응답으로 옮긴다. base 예외 하나만 잡는다 —
 * 그 범위는 {@code com.ticket.error.ExceptionHandlerScopeTest}가 강제한다.
 */
@Slf4j
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ShowLikeExceptionHandler {

    @ExceptionHandler(ShowLikeException.class)
    public ResponseEntity<ApiResponse<Object>> handleShowLikeException(final ShowLikeException exception) {
        log.info("showlike.rejected: code={}", exception.getErrorCode().getCode());

        return ResponseEntity
                .status(exception.getStatus())
                .body(ApiResponse.error(
                        exception.getErrorCode().getCode(), exception.getMessage(), exception.getData()));
    }
}
