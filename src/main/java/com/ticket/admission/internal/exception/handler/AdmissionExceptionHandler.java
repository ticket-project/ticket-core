package com.ticket.admission.internal.exception.handler;

import com.ticket.admission.internal.exception.AdmissionTokenException;
import com.ticket.shared.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * admission 오류를 응답으로 옮긴다.
 *
 * <p>이 module에는 controller가 없다 — admission 예외는 booking의 예매 요청 처리 중에 던져진다.
 * 그래도 handler를 이 module이 소유하는 이유는 검증 실패 사유를 admission의 어휘로 로깅하고, 공개
 * 메시지를 사유와 분리하는 정책을 module 안에서 강제하기 위해서다.
 *
 * <p>base 예외 하나만 잡는다. 상위 타입을 잡으면 order가 높아 다른 module의 오류까지 삼킨다 —
 * 그 범위는 {@code com.ticket.error.ExceptionHandlerScopeTest}가 강제한다.
 */
@Slf4j
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AdmissionExceptionHandler {

    @ExceptionHandler(AdmissionTokenException.class)
    public ResponseEntity<ApiResponse<Object>> handleAdmissionTokenException(
            final AdmissionTokenException exception
    ) {
        log.info("admission.rejected: code={}, reason={}", exception.getErrorCode().getCode(), exception.getReason());

        return ResponseEntity
                .status(exception.getStatus())
                .body(ApiResponse.error(
                        exception.getErrorCode().getCode(), exception.getMessage(), exception.getData()));
    }
}
