package com.ticket.like.exception.handler;

import com.ticket.like.exception.LikeException;
import com.ticket.web.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * like 오류를 응답으로 옮긴다. base 예외 하나만 잡는다 —
 * 그 범위는 {@code com.ticket.error.ExceptionHandlerScopeTest}가 강제한다.
 *
 * <p>{@link LikeException}은 상태를 모른다. 지금 구체 타입은 {@code LikeAlreadyExistsException}
 * 하나뿐이고 409로 응답한다 — 두 번째 타입이 생기면 {@code BookingExceptionHandler}처럼 타입별
 * switch로 바꾼다.
 */
@Slf4j
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class LikeExceptionHandler {

    @ExceptionHandler(LikeException.class)
    public ResponseEntity<ApiResponse<Object>> handleLikeException(final LikeException exception) {
        log.info("like.rejected: code={}", exception.getErrorCode().getCode());

        return ResponseEntity
                .status(HttpStatus.CONFLICT.value())
                .body(ApiResponse.error(
                        exception.getErrorCode().getCode(), exception.getMessage(), exception.getData()));
    }
}
