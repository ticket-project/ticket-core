package com.ticket.booking.exception;

import org.springframework.http.HttpStatus;

/**
 * 예매 마감 시각을 지난 회차다.
 */
public class PerformanceIsPastException extends BookingException {

    private static final String MESSAGE = "과거 공연은 예매할 수 없습니다.";

    public PerformanceIsPastException() {
        this(null);
    }

    public PerformanceIsPastException(final Object data) {
        super(HttpStatus.BAD_REQUEST, BookingErrorCode.E3001, MESSAGE, data);
    }
}
