package com.ticket.core.support;

import com.ticket.core.support.exception.CoreException;
import com.ticket.core.support.exception.ErrorType;
import com.ticket.core.support.response.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * 모든 오류를 하나의 응답 형식으로 직렬화한다.
 *
 * <p>{@link CoreException}은 {@link ErrorType}을 그대로 쓰므로 기능 오류를 추가해도 이 클래스는
 * 수정하지 않는다. 나머지 핸들러는 Spring이 {@link ErrorType} 없이 던지는 예외를 고정된 {@link ErrorType}
 * 값으로 옮기는 변환이다.
 */
@RestControllerAdvice
public class ApiControllerAdvice {

    private static final Logger log = LoggerFactory.getLogger(ApiControllerAdvice.class);

    @ExceptionHandler(CoreException.class)
    public ResponseEntity<ApiResponse<Object>> handleCoreException(final CoreException exception) {
        return toResponse(exception.getErrorType(), exception.getData());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Object>> handleMethodArgumentNotValidException(
            final MethodArgumentNotValidException exception
    ) {
        final String fieldErrors = exception.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .collect(Collectors.joining("; "));

        return toResponse(ErrorType.INVALID_REQUEST, fieldErrors);
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

        return toResponse(ErrorType.INVALID_REQUEST, parameterErrors);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Object>> handleHttpMessageNotReadableException(
            final HttpMessageNotReadableException exception
    ) {
        return toResponse(ErrorType.INVALID_REQUEST, null);
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ApiResponse<Object>> handleNoHandlerFoundException(
            final NoHandlerFoundException exception
    ) {
        return toResponse(ErrorType.API_NOT_FOUND, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleException(final Exception exception) {
        log.error("예외가 발생했습니다. message={} ", exception.getMessage(), exception);
        return toResponse(ErrorType.DEFAULT_ERROR, null);
    }

    private String parameterName(final ParameterValidationResult result) {
        final String name = result.getMethodParameter().getParameterName();
        return name != null ? name : "parameter" + result.getMethodParameter().getParameterIndex();
    }

    private ResponseEntity<ApiResponse<Object>> toResponse(final ErrorType error, final Object data) {
        return ResponseEntity
                .status(error.getStatus().value())
                .body(ApiResponse.error(error, data));
    }
}
