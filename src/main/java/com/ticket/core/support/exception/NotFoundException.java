package com.ticket.core.support.exception;

/**
 * 데이터 조회 실패를 나타낸다. {@link ApiControllerAdvice}는 {@link CoreException} handler 하나로
 * 이 예외를 함께 처리한다.
 */
public final class NotFoundException extends CoreException {

    public NotFoundException(final ErrorType errorType) {
        super(errorType);
    }

    public NotFoundException(final ErrorType errorType, final Object data) {
        super(errorType, data);
    }
}
