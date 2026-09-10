package com.ticket.booking.exception;


/**
 * 다른 회원이 이미 선택 중인 좌석이다.
 */
public class SeatAlreadySelectedException extends BookingException {

    private static final String MESSAGE = "이미 선택된 좌석입니다.";

    public SeatAlreadySelectedException() {
        this(null);
    }

    public SeatAlreadySelectedException(final Object data) {
        super(BookingErrorCode.E4001, MESSAGE, data);
    }
}
