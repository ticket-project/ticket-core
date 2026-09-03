package com.ticket.booking.internal.exception;

import org.springframework.http.HttpStatus;

/**
 * 본인 주문이 아닌 주문에 접근했다.
 */
public class OrderNotOwnedException extends BookingException {

    private static final String MESSAGE = "본인 주문만 처리할 수 있습니다.";

    public OrderNotOwnedException() {
        this(null);
    }

    public OrderNotOwnedException(final Object data) {
        super(HttpStatus.FORBIDDEN, BookingErrorCode.E5003, MESSAGE, data);
    }
}
