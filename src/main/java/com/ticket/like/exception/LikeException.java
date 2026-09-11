package com.ticket.like.exception;

import com.ticket.shared.exception.ErrorCode;
import com.ticket.shared.exception.TicketException;

/**
 * like 업무 오류의 base 타입이다. 이 module의 handler는 이 타입 하나만 잡는다. HTTP 상태는
 * 예외가 아니라 handler가 안다 — LikeExceptionHandler가 구체 타입별로
 * 정한다.
 */
public abstract class LikeException extends TicketException {

    protected LikeException(
            final ErrorCode errorCode,
            final String message,
            final Object data
    ) {
        super(errorCode, message, data);
    }
}
