package com.ticket.booking.exception;

/** 회차가 정한 1인 선점 좌석 수 한도를 넘었다. */
public final class HoldLimitExceededException extends BookingException {
    private static final String MESSAGE = "선점 가능한 좌석 수를 초과하였습니다.";

    public HoldLimitExceededException() {
        super(BookingErrorCode.E6001, MESSAGE, null);
    }
}
