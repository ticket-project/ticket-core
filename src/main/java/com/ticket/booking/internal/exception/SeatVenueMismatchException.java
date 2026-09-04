package com.ticket.booking.internal.exception;

import org.springframework.http.HttpStatus;

/**
 * 판매 좌석 편성 요청의 좌석이 그 회차의 Venue에 속하지 않는다.
 */
public class SeatVenueMismatchException extends BookingException {

    private static final String MESSAGE = "요청한 좌석이 이 회차의 공연장에 속하지 않습니다.";

    public SeatVenueMismatchException() {
        this(null);
    }

    public SeatVenueMismatchException(final Object data) {
        super(HttpStatus.BAD_REQUEST, BookingErrorCode.E4003, MESSAGE, data);
    }
}
