package com.ticket.booking.exception;

import org.springframework.http.HttpStatus;

/**
 * 이미 판매 좌석으로 편성된 (performance, seat) 조합을 다시 편성하려 했다.
 */
public class PerformanceSeatAlreadyEditionedException extends BookingException {

    private static final String MESSAGE = "이미 편성된 좌석입니다.";

    public PerformanceSeatAlreadyEditionedException() {
        this(null);
    }

    public PerformanceSeatAlreadyEditionedException(final Object data) {
        super(HttpStatus.BAD_REQUEST, BookingErrorCode.E4005, MESSAGE, data);
    }
}
