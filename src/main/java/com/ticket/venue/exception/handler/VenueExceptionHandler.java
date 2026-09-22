package com.ticket.venue.exception.handler;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.ticket.shared.web.ApiResponse;
import com.ticket.venue.exception.VenueNotFoundException;

import lombok.extern.slf4j.Slf4j;

/**
 * venue 오류를 응답으로 옮긴다. 자기 module의 예외만 잡는다 — 그 범위는 {@code com.ticket.shared.exception.ExceptionHandlerScopeTest}가 강제한다.
 *
 * <p>지금 구체 타입은 {@link VenueNotFoundException} 하나뿐이고 404로 응답한다. 상태와 오류 코드는
 * {@code com.ticket.shared.exception.NotFoundException}이 정한 것과 같다 — 두 번째 타입이 생기면 {@code BookingExceptionHandler}처럼 타입별
 * switch로 바꾼다.
 */
@Slf4j
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class VenueExceptionHandler {
    @ExceptionHandler(VenueNotFoundException.class)
    public ResponseEntity<ApiResponse<Object>> handleVenueNotFound(final VenueNotFoundException exception) {
        log.info("venue.rejected: code={}", exception.getErrorCode().getCode());

        return ResponseEntity.status(HttpStatus.NOT_FOUND.value())
                .body(ApiResponse.error(
                        exception.getErrorCode().getCode(), exception.getMessage(), exception.getData()));
    }
}
