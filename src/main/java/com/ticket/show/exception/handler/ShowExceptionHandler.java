package com.ticket.show.exception.handler;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.ticket.shared.web.ApiResponse;
import com.ticket.show.exception.UnsupportedShowSortException;

import lombok.extern.slf4j.Slf4j;

/**
 * show 오류를 응답으로 옮긴다. 자기 module 예외만 잡는다 — 그 범위는 {@code com.ticket.shared.exception.ExceptionHandlerScopeTest}가 강제한다.
 *
 * <p>show 업무 오류는 {@link UnsupportedShowSortException} 하나뿐이라 base 예외 없이 직접 잡고 400으로 응답한다. 두 번째 타입이 생기면 sealed base와
 * {@code BookingExceptionHandler}처럼 타입별 switch를 둔다.
 */
@Slf4j
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ShowExceptionHandler {
    @ExceptionHandler(UnsupportedShowSortException.class)
    public ResponseEntity<ApiResponse<Object>> handleShowException(final UnsupportedShowSortException exception) {
        log.info("show.rejected: code={}", exception.getErrorCode().getCode());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST.value())
                .body(ApiResponse.error(
                        exception.getErrorCode().getCode(), exception.getMessage(), exception.getData()));
    }
}
