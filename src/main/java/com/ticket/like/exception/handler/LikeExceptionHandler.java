package com.ticket.like.exception.handler;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.ticket.like.exception.LikeAlreadyExistsException;
import com.ticket.shared.web.ApiResponse;

import lombok.extern.slf4j.Slf4j;

/**
 * like 오류를 응답으로 옮긴다. 자기 module 예외만 잡는다 — 그 범위는 {@code com.ticket.shared.exception.ExceptionHandlerScopeTest}가 강제한다.
 *
 * <p>like 업무 오류는 {@link LikeAlreadyExistsException} 하나뿐이라 base 예외 없이 직접 잡고 409로 응답한다. 두 번째 타입이 생기면 sealed base와
 * {@code BookingExceptionHandler}처럼 타입별 switch를 둔다.
 */
@Slf4j
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class LikeExceptionHandler {
    @ExceptionHandler(LikeAlreadyExistsException.class)
    public ResponseEntity<ApiResponse<Object>> handleLikeException(final LikeAlreadyExistsException exception) {
        log.info("like.rejected: code={}", exception.getErrorCode().getCode());

        return ResponseEntity.status(HttpStatus.CONFLICT.value())
                .body(ApiResponse.error(
                        exception.getErrorCode().getCode(), exception.getMessage(), exception.getData()));
    }
}
