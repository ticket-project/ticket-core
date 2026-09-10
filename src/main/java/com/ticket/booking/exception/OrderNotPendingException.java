package com.ticket.booking.exception;


/**
 * 결제 대기 상태가 아닌 주문을 처리하려 했다.
 */
public class OrderNotPendingException extends BookingException {

    private static final String MESSAGE = "결제 대기 주문만 처리할 수 있습니다.";

    public OrderNotPendingException() {
        this(null);
    }

    public OrderNotPendingException(final Object data) {
        super(BookingErrorCode.E5002, MESSAGE, data);
    }
}
