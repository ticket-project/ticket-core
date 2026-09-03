package com.ticket.error;

import org.springframework.http.HttpStatus;

import java.util.Objects;

/**
 * 모든 업무 예외의 기반이다. 예외 하나가 외부에 보이는 완성된 계약(HTTP 상태, 코드, 공개 메시지,
 * 부가 정보)을 스스로 들고 있으므로 handler는 그것을 옮겨 담기만 한다.
 *
 * <p><b>{@code message}와 {@code data}를 바꿔 담지 않는다.</b> {@code message}는 오류마다 정해진
 * 공개 문구이고 {@code data}는 그 오류를 좁히는 부가 정보다(검증 실패 필드 목록, 인증 실패 사유 등).
 * 응답에서 각각 {@code error.message}와 {@code error.data}가 된다.
 */
public abstract class TicketException extends RuntimeException {

    private final HttpStatus status;
    private final ErrorCode errorCode;
    private final transient Object data;

    protected TicketException(final HttpStatus status, final ErrorCode errorCode, final String message) {
        this(status, errorCode, message, null);
    }

    protected TicketException(
            final HttpStatus status,
            final ErrorCode errorCode,
            final String message,
            final Object data
    ) {
        this(status, errorCode, message, data, null);
    }

    /**
     * 원인 예외를 함께 보관한다. {@code cause}는 로그에만 쓰이고 응답에 노출되지 않는다.
     */
    protected TicketException(
            final HttpStatus status,
            final ErrorCode errorCode,
            final String message,
            final Object data,
            final Throwable cause
    ) {
        super(message, cause);
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode must not be null");
        this.data = data;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public Object getData() {
        return data;
    }
}
