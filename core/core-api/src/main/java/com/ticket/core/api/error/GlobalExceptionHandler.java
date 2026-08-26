package com.ticket.core.api.error;

import com.ticket.core.support.response.ApiResponse;
import com.ticket.support.error.AuthException;
import com.ticket.support.error.CoreException;
import com.ticket.support.error.ErrorDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.stream.Collectors;

/**
 * 모든 오류를 하나의 응답 형식으로 직렬화한다.
 *
 * <p>CoreException은 ErrorDefinition을 그대로 쓰므로 기능 오류를 추가해도 이 클래스는 수정하지 않는다.
 * 나머지 핸들러는 Spring이 ErrorDefinition 없이 던지는 예외를 ApiErrorType으로 옮기는 고정 변환이다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(CoreException.class)
    public ResponseEntity<ApiResponse<Object>> handleCoreException(final CoreException exception) {
        return toResponse(exception.getErrorType(), exception.getData());
    }

    @Deprecated
    @ExceptionHandler(AuthException.class)
    public ResponseEntity<ApiResponse<Object>> handleAuthException(final AuthException exception) {
        return toResponse(exception.getErrorType(), exception.getData());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Object>> handleMethodArgumentNotValidException(
            final MethodArgumentNotValidException exception
    ) {
        final String fieldErrors = exception.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .collect(Collectors.joining("; "));

        return toResponse(ApiErrorType.INVALID_REQUEST, fieldErrors);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Object>> handleHttpMessageNotReadableException(
            final HttpMessageNotReadableException exception
    ) {
        return toResponse(ApiErrorType.INVALID_REQUEST, null);
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ApiResponse<Object>> handleNoHandlerFoundException(
            final NoHandlerFoundException exception
    ) {
        return toResponse(ApiErrorType.API_NOT_FOUND, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleException(final Exception exception) {
        log.error("예외가 발생했습니다. message={} ", exception.getMessage(), exception);
        return toResponse(ApiErrorType.INTERNAL_SERVER_ERROR, null);
    }

    private ResponseEntity<ApiResponse<Object>> toResponse(final ErrorDefinition error, final Object data) {
        return ResponseEntity
                .status(error.getStatus().value())
                .body(ApiResponse.error(error, data));
    }
}
