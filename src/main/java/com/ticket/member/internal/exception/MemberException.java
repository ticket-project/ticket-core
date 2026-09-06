package com.ticket.member.internal.exception;

import com.ticket.error.ErrorCode;
import com.ticket.error.TicketException;
import org.springframework.http.HttpStatus;

/**
 * member 업무 오류의 base 타입이다. 이 module의 handler는 이 타입 하나만 잡는다.
 */
public abstract class MemberException extends TicketException {

    protected MemberException(
            final HttpStatus status,
            final ErrorCode errorCode,
            final String message,
            final Object data
    ) {
        super(status, errorCode, message, data);
    }
}
