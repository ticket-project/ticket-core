package com.ticket.core.support.exception;

/**
 * 인증·인가 실패를 나타낸다. {@link ApiControllerAdvice}는 {@link CoreException} handler 하나로
 * 이 예외를 함께 처리한다.
 */
public final class AuthException extends CoreException {

    public AuthException(final ErrorType errorType) {
        super(errorType);
    }

    public AuthException(final ErrorType errorType, final Object data) {
        super(errorType, data);
    }
}
