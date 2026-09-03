package com.ticket.error.handler;

import com.ticket.error.CommonErrorCode;
import com.ticket.error.ErrorCode;
import com.ticket.error.InternalErrorException;
import com.ticket.error.InvalidRequestException;
import com.ticket.error.TicketException;
import com.ticket.web.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.stream.Collectors;

/**
 * 모든 오류를 하나의 응답 형식으로 직렬화하는 마지막 handler다.
 *
 * <p>{@link TicketException}은 상태·코드·메시지를 스스로 들고 있으므로 업무 오류를 추가해도 이
 * 클래스는 수정하지 않는다. 나머지 handler는 Spring이 계약 없이 던지는 예외를 고정된 공통 오류로
 * 옮기는 변환이다.
 *
 * <p><b>{@link Ordered#LOWEST_PRECEDENCE}인 이유</b>: Spring은 advice를 order로 정렬한 뒤 매칭되는
 * 메서드를 가진 <i>첫</i> advice에서 멈춘다. 여기 있는 {@code Exception} fallback이 먼저 잡히면 각
 * module의 handler가 영영 호출되지 않는다. module handler는 반대로 {@link Ordered#HIGHEST_PRECEDENCE}를
 * 쓰고 자기 module의 base 예외만 잡는다 — 그 범위는
 * {@code com.ticket.error.ExceptionHandlerScopeTest}가 강제한다.
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(TicketException.class)
    public ResponseEntity<ApiResponse<Object>> handleTicketException(final TicketException exception) {
        return toResponse(exception);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Object>> handleMethodArgumentNotValidException(
            final MethodArgumentNotValidException exception
    ) {
        final String fieldErrors = exception.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .collect(Collectors.joining("; "));

        return toResponse(new InvalidRequestException(fieldErrors));
    }

    /**
     * path·query·header 파라미터의 Bean Validation 실패다. 요청 body 실패와 같은 400 계약으로 맞춘다.
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiResponse<Object>> handleHandlerMethodValidationException(
            final HandlerMethodValidationException exception
    ) {
        final String parameterErrors = exception.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> parameterName(result) + ": " + error.getDefaultMessage()))
                .collect(Collectors.joining("; "));

        return toResponse(new InvalidRequestException(parameterErrors));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Object>> handleHttpMessageNotReadableException(
            final HttpMessageNotReadableException exception
    ) {
        return toResponse(new InvalidRequestException());
    }

    /**
     * 없는 API 경로다. 데이터 없음(E404)과 코드는 같고 공개 문구만 다르므로 예외 타입을 두지 않는다.
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ApiResponse<Object>> handleNoHandlerFoundException(
            final NoHandlerFoundException exception
    ) {
        return toResponse(HttpStatus.NOT_FOUND, CommonErrorCode.E404, "요청한 API를 찾을 수 없습니다.", null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleException(final Exception exception) {
        log.error("예외가 발생했습니다. message={} ", exception.getMessage(), exception);
        return toResponse(new InternalErrorException());
    }

    private String parameterName(final ParameterValidationResult result) {
        final String name = result.getMethodParameter().getParameterName();
        return name != null ? name : "parameter" + result.getMethodParameter().getParameterIndex();
    }

    private ResponseEntity<ApiResponse<Object>> toResponse(final TicketException exception) {
        return toResponse(
                exception.getStatus(), exception.getErrorCode(), exception.getMessage(), exception.getData());
    }

    private ResponseEntity<ApiResponse<Object>> toResponse(
            final HttpStatus status,
            final ErrorCode errorCode,
            final String message,
            final Object data
    ) {
        return ResponseEntity
                .status(status.value())
                .body(ApiResponse.error(errorCode.getCode(), message, data));
    }
}
