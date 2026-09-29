package com.ticket.booking.exception;

/** 본인 주문이 아닌 주문에 접근했다. */
public final class OrderNotOwnedException extends BookingException {
    private static final String MESSAGE = "본인 주문만 처리할 수 있습니다.";

    public OrderNotOwnedException() {
        super(BookingErrorCode.E5003, MESSAGE, null);
    }
}
