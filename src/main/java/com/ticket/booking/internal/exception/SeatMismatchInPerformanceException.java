package com.ticket.booking.internal.exception;

import org.springframework.http.HttpStatus;

/**
 * 요청한 좌석이 그 회차의 좌석이 아니다.
 */
public class SeatMismatchInPerformanceException extends BookingException {

    private static final String MESSAGE = "요청한 좌석 정보와 일치하지 않습니다.";

    public SeatMismatchInPerformanceException() {
        this(null);
    }

    public SeatMismatchInPerformanceException(final Object data) {
        super(HttpStatus.BAD_REQUEST, BookingErrorCode.E4000, MESSAGE, data);
    }
}
