package com.ticket.member.exception.handler;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.ticket.member.exception.DuplicateEmailException;
import com.ticket.shared.web.ApiResponse;

import lombok.extern.slf4j.Slf4j;

/**
 * member 오류를 응답으로 옮긴다.
 *
 * <p>base 예외 하나만 잡는다 — 그 범위는 {@code com.ticket.shared.exception.ExceptionHandlerScopeTest}가 강제한다.
 * {@link DuplicateEmailException}은 상태를 모른다 — HTTP 상태는 이 handler가 안다.
 */
@Slf4j
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MemberExceptionHandler {
    @ExceptionHandler(DuplicateEmailException.class)
    public ResponseEntity<ApiResponse<Object>> handleMemberException(final DuplicateEmailException exception) {
        log.info("member.rejected: code={}", exception.getErrorCode().getCode());

        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(
                        exception.getErrorCode().getCode(), exception.getMessage(), exception.getData()));
    }
}
