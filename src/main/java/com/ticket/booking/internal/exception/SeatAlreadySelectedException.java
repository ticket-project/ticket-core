package com.ticket.booking.internal.exception;

import org.springframework.http.HttpStatus;

/**
 * 다른 회원이 이미 선택 중인 좌석이다.
 */
public class SeatAlreadySelectedException extends BookingException {

    private static final String MESSAGE = "이미 선택된 좌석입니다.";

    public SeatAlreadySelectedException() {
        this(null);
    }

    public SeatAlreadySelectedException(final Object data) {
        super(HttpStatus.CONFLICT, BookingErrorCode.E4001, MESSAGE, data);
    }
}
