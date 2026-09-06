package com.ticket.catalog.exception.handler;

import com.ticket.catalog.exception.CatalogException;
import com.ticket.web.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * catalog 오류를 응답으로 옮긴다. base 예외 하나만 잡는다 —
 * 그 범위는 {@code com.ticket.error.ExceptionHandlerScopeTest}가 강제한다.
 */
@Slf4j
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CatalogExceptionHandler {

    @ExceptionHandler(CatalogException.class)
    public ResponseEntity<ApiResponse<Object>> handleCatalogException(final CatalogException exception) {
        log.info("catalog.rejected: code={}", exception.getErrorCode().getCode());

        return ResponseEntity
                .status(exception.getStatus())
                .body(ApiResponse.error(
                        exception.getErrorCode().getCode(), exception.getMessage(), exception.getData()));
    }
}
