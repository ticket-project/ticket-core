package com.ticket.booking.support.exception;


/**
 * 다른 회원이 이미 선점(hold)한 좌석이다.
 */
public class SeatAlreadyHoldException extends BookingException {

    private static final String MESSAGE = "좌석이 이미 선점되었습니다.";

    public SeatAlreadyHoldException() {
        this(null);
    }

    public SeatAlreadyHoldException(final Object data) {
        super(BookingErrorCode.E6000, MESSAGE, data);
    }
}
