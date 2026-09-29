package com.ticket.booking.exception;

/** 아직 예매 오픈 시각 전이다. */
public final class BookingNotOpenYetException extends BookingException {
    private static final String MESSAGE = "아직 예매가 오픈되지 않았습니다.";

    public BookingNotOpenYetException() {
        super(BookingErrorCode.E3002, MESSAGE, null);
    }
}
