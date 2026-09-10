package com.ticket.booking.support.exception;

import com.ticket.error.ErrorCode;
import com.ticket.error.TicketException;

/**
 * booking 업무 오류의 base 타입이다. 이 module의 handler는 이 타입 하나만 잡는다. HTTP 상태는
 * 예외가 아니라 handler가 안다 — BookingExceptionHandler가 구체 타입별로
 * 정한다.
 */
public abstract class BookingException extends TicketException {

    protected BookingException(
            final ErrorCode errorCode,
            final String message,
            final Object data
    ) {
        super(errorCode, message, data);
    }
}
