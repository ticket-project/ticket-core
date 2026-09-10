package com.ticket.error;

import java.util.Objects;

/**
 * 모든 업무 예외의 기반이다. 예외는 실패의 의미(errorCode)와 그것을 좁히는 부가 정보(data)를
 * 전달하는 그릇일 뿐이다 — HTTP로 어떻게 응답할지는 모른다.
 *
 * <p><b>HTTP 상태를 들고 있지 않는다.</b> 어떤 상태 코드로 응답할지는 이 오류를 처리하는 웹
 * 계층(각 module의 handler, 공통 오류는 {@code com.ticket.error.handler.GlobalExceptionHandler})이
 * 안다 — 같은 업무 실패도 어느 채널로 나가느냐에 따라 표현이 달라질 수 있고, 예외는 Spring
 * Web/HTTP 타입을 몰라야 재사용 가능하다.
 *
 * <p><b>{@code message}와 {@code data}를 바꿔 담지 않는다.</b> {@code message}는 오류마다 정해진
 * 공개 문구이고 {@code data}는 그 오류를 좁히는 부가 정보다(검증 실패 필드 목록, 인증 실패 사유 등).
 * 응답에서 각각 {@code error.message}와 {@code error.data}가 된다.
 */
public abstract class TicketException extends RuntimeException {

    private final ErrorCode errorCode;
    private final transient Object data;

    protected TicketException(final ErrorCode errorCode, final String message) {
        this(errorCode, message, null);
    }

    protected TicketException(final ErrorCode errorCode, final String message, final Object data) {
        this(errorCode, message, data, null);
    }

    /**
     * 원인 예외를 함께 보관한다. {@code cause}는 로그에만 쓰이고 응답에 노출되지 않는다.
     */
    protected TicketException(
            final ErrorCode errorCode,
            final String message,
            final Object data,
            final Throwable cause
    ) {
        super(message, cause);
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode must not be null");
        this.data = data;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public Object getData() {
        return data;
    }
}
