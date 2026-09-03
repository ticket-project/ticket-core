package com.ticket.booking.internal.exception;

import org.springframework.http.HttpStatus;

/**
 * 본인이 선택하지 않은 좌석을 해제하려 했다.
 */
public class SeatNotOwnedException extends BookingException {

    private static final String MESSAGE = "본인이 선택한 좌석만 해제할 수 있습니다.";

    public SeatNotOwnedException() {
        this(null);
    }

    public SeatNotOwnedException(final Object data) {
        super(HttpStatus.FORBIDDEN, BookingErrorCode.E4002, MESSAGE, data);
    }
}
