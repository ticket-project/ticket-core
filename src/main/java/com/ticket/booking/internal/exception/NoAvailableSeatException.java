package com.ticket.booking.internal.exception;

import org.springframework.http.HttpStatus;

/**
 * 요청한 좌석 중 판매 가능한 것이 없다.
 */
public class NoAvailableSeatException extends BookingException {

    private static final String MESSAGE = "이용 가능한 좌석이 없습니다.";

    public NoAvailableSeatException() {
        this(null);
    }

    public NoAvailableSeatException(final Object data) {
        super(HttpStatus.BAD_REQUEST, BookingErrorCode.E3003, MESSAGE, data);
    }
}
