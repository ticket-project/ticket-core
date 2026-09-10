package com.ticket.booking.exception;


/**
 * 아직 예매 오픈 시각 전이다.
 */
public class NotYetReserveTimeException extends BookingException {

    private static final String MESSAGE = "아직 예매가 오픈되지 않았습니다.";

    public NotYetReserveTimeException() {
        this(null);
    }

    public NotYetReserveTimeException(final Object data) {
        super(BookingErrorCode.E3002, MESSAGE, data);
    }
}
