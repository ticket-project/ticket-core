package com.ticket.core.support.exception;

import lombok.Getter;

/**
 * HTTP 오류 응답 본문이다. 클라이언트는 message가 아니라 code로 분기한다.
 */
@Getter
public class ErrorMessage {

    private final String code;
    private final String message;
    private final Object data;

    public ErrorMessage(final String code, final String message, final Object data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public ErrorMessage(final ErrorType errorType, final Object data) {
        this(errorType.getErrorCode().getCode(), errorType.getMessage(), data);
    }
}
