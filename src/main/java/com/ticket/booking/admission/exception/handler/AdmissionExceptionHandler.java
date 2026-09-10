package com.ticket.booking.admission.exception.handler;

import com.ticket.booking.admission.exception.AdmissionTokenException;
import com.ticket.shared.web.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * admission 오류를 응답으로 옮긴다.
 *
 * <p>admission 예외는 booking의 예매 요청 처리 중에 던져진다. {@code BookingExceptionHandler}와 별도
 * advice로 두는 이유는 검증 실패 사유를 admission의 어휘로 로깅하고, 공개 메시지를 사유와 분리하는
 * 정책을 한 곳에서 강제하기 위해서다(원래 별도 admission module의 handler였다).
 *
 * <p>base 예외 하나만 잡는다. 상위 타입을 잡으면 order가 높아 다른 module의 오류까지 삼킨다 —
 * 그 범위는 {@code com.ticket.shared.exception.ExceptionHandlerScopeTest}가 강제한다.
 *
 * <p>admission 예외 3종(required/expired/invalid)은 모두 403으로 응답한다 — 상태를 예외가 아니라
 * 이 handler가 정한다.
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
                .status(HttpStatus.FORBIDDEN.value())
                .body(ApiResponse.error(
                        exception.getErrorCode().getCode(), exception.getMessage(), exception.getData()));
    }
}
