package com.ticket.member.exception;

import com.ticket.shared.exception.ErrorCode;
import com.ticket.shared.exception.TicketException;

/**
 * member 업무 오류의 base 타입이다. 이 module의 handler는 이 타입 하나만 잡는다. HTTP 상태는
 * 예외가 아니라 handler가 안다 — MemberExceptionHandler가 구체 타입별로
 * 정한다.
 */
public abstract class MemberException extends TicketException {

    protected MemberException(
            final ErrorCode errorCode,
            final String message,
            final Object data
    ) {
        super(errorCode, message, data);
    }
}
