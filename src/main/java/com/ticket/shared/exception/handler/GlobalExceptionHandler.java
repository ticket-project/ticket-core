package com.ticket.shared.exception.handler;

import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.ticket.shared.exception.CommonErrorCode;
import com.ticket.shared.exception.ErrorCode;
import com.ticket.shared.exception.InternalErrorException;
import com.ticket.shared.exception.InvalidRequestException;
import com.ticket.shared.exception.NotFoundException;
import com.ticket.shared.exception.TicketException;
import com.ticket.shared.web.ApiResponse;

import lombok.extern.slf4j.Slf4j;

/**
 * 모든 오류를 하나의 응답 형식으로 직렬화하는 마지막 handler다.
 *
 * <p>업무 코드는 실패의 의미(errorCode)와 부가 정보(data)만 예외로 전달한다. HTTP 상태는 그 오류를 처리하는 쪽이 안다 - 여기서는 어느 module에도 속하지 않는 공통 오류 셋
 * ({@link InvalidRequestException}/{@link NotFoundException}/{@link InternalErrorException})의 상태를 이 handler가 고정하고,
 * module 고유 오류의 상태는 각 module의 handler가 고정한다(예: {@code BookingExceptionHandler}). 새 공통 오류가 생기지 않는 한 이 클래스는 수정하지 않는다.
 *
 * <p><b>{@link Ordered#LOWEST_PRECEDENCE}인 이유</b>: Spring은 advice를 order로 정렬한 뒤 매칭되는 메서드를 가진 <i>첫</i> advice에서 멈춘다. 여기
 * 있는 {@code Exception} fallback이 먼저 잡히면 각 module의 handler가 영영 호출되지 않는다. module handler는 반대로
 * {@link Ordered#HIGHEST_PRECEDENCE}를 쓰고 자기 module의 base 예외만 잡는다 - 그 범위는
 * {@code com.ticket.shared.exception.ExceptionHandlerScopeTest}가 강제한다.
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
@Slf4j
public class GlobalExceptionHandler {
    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<ApiResponse<Object>> handleInvalidRequestException(final InvalidRequestException exception) {
        return toResponse(HttpStatus.BAD_REQUEST, exception);
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiResponse<Object>> handleNotFoundException(final NotFoundException exception) {
        return toResponse(HttpStatus.NOT_FOUND, exception);
    }

    @ExceptionHandler(InternalErrorException.class)
    public ResponseEntity<ApiResponse<Object>> handleInternalErrorException(final InternalErrorException exception) {
        return toResponse(HttpStatus.INTERNAL_SERVER_ERROR, exception);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Object>> handleMethodArgumentNotValidException(
            final MethodArgumentNotValidException exception) {
        final String fieldErrors = exception.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .collect(Collectors.joining("; "));

        return toResponse(HttpStatus.BAD_REQUEST, new InvalidRequestException(fieldErrors));
    }

    /** path·query·header 파라미터의 Bean Validation 실패다. 요청 body 실패와 같은 400 계약으로 맞춘다. */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiResponse<Object>> handleHandlerMethodValidationException(
            final HandlerMethodValidationException exception) {
        final String parameterErrors = exception.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> parameterName(result) + ": " + error.getDefaultMessage()))
                .collect(Collectors.joining("; "));

        return toResponse(HttpStatus.BAD_REQUEST, new InvalidRequestException(parameterErrors));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Object>> handleHttpMessageNotReadableException(
            final HttpMessageNotReadableException exception) {
        return toResponse(HttpStatus.BAD_REQUEST, new InvalidRequestException());
    }

    /** path·query 값을 선언한 타입으로 바꿀 수 없다(예: {@code /api/v1/shows/abc}). 어느 파라미터인지만 공개한다. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Object>> handleMethodArgumentTypeMismatchException(
            final MethodArgumentTypeMismatchException exception) {
        log.debug("요청 파라미터 형식 오류: {}", exception.getMessage());
        return toResponse(
                HttpStatus.BAD_REQUEST, new InvalidRequestException(exception.getName() + ": 형식이 올바르지 않습니다."));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Object>> handleMissingServletRequestParameterException(
            final MissingServletRequestParameterException exception) {
        log.debug("필수 요청 파라미터 누락: {}", exception.getMessage());
        return toResponse(
                HttpStatus.BAD_REQUEST, new InvalidRequestException(exception.getParameterName() + ": 값이 필요합니다."));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiResponse<Object>> handleMissingRequestHeaderException(
            final MissingRequestHeaderException exception) {
        log.debug("필수 요청 header 누락: {}", exception.getMessage());
        return toResponse(
                HttpStatus.BAD_REQUEST, new InvalidRequestException(exception.getHeaderName() + ": 값이 필요합니다."));
    }

    /**
     * 없는 경로다. 데이터 없음(E404)과 코드는 같고 공개 문구만 다르므로 예외 타입을 두지 않는다.
     *
     * <p>정적 리소스({@code /api/images/**}) 처리가 켜져 있어 mapping이 없는 경로는 {@link NoHandlerFoundException}이 아니라 마지막 resource
     * handler의 {@link NoResourceFoundException}으로 끝난다. 둘 다 같은 404다.
     */
    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<ApiResponse<Object>> handleNoHandlerFoundException(final Exception exception) {
        log.debug("없는 경로 요청: {}", exception.getMessage());
        return toResponse(HttpStatus.NOT_FOUND, CommonErrorCode.E404, "요청한 API를 찾을 수 없습니다.", null);
    }

    /** 경로는 있으나 method가 다르다. 프레임워크 요청 오류라 E400을 쓰고, 구분은 HTTP 상태가 한다. */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Object>> handleHttpRequestMethodNotSupportedException(
            final HttpRequestMethodNotSupportedException exception) {
        log.debug("지원하지 않는 method: {}", exception.getMessage());
        return toResponse(
                HttpStatus.METHOD_NOT_ALLOWED,
                new InvalidRequestException(exception.getMethod() + " method를 지원하지 않습니다."));
    }

    /** body의 Content-Type을 받지 않는다. 405와 같은 이유로 E400을 쓴다. */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Object>> handleHttpMediaTypeNotSupportedException(
            final HttpMediaTypeNotSupportedException exception) {
        log.debug("지원하지 않는 Content-Type: {}", exception.getMessage());
        return toResponse(HttpStatus.UNSUPPORTED_MEDIA_TYPE, new InvalidRequestException());
    }

    /** 위에서 잡지 못한 예외는 서버 결함으로 본다. 클라이언트 입력 오류를 여기로 흘리면 4xx가 500과 ERROR 로그가 된다. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleException(final Exception exception) {
        log.error("예외가 발생했습니다. message={} ", exception.getMessage(), exception);
        return toResponse(HttpStatus.INTERNAL_SERVER_ERROR, new InternalErrorException());
    }

    private String parameterName(final ParameterValidationResult result) {
        final String name = result.getMethodParameter().getParameterName();
        return name != null ? name : "parameter" + result.getMethodParameter().getParameterIndex();
    }

    private ResponseEntity<ApiResponse<Object>> toResponse(final HttpStatus status, final TicketException exception) {
        return toResponse(status, exception.getErrorCode(), exception.getMessage(), exception.getData());
    }

    private ResponseEntity<ApiResponse<Object>> toResponse(
            final HttpStatus status, final ErrorCode errorCode, final String message, final @Nullable Object data) {
        return ResponseEntity.status(status.value()).body(ApiResponse.error(errorCode.getCode(), message, data));
    }
}
