package com.ticket.booking.exception;


/**
 * 판매 좌석 편성 요청의 PerformanceGrade가 그 회차에 속하지 않는다.
 */
public class PerformanceGradeMismatchException extends BookingException {

    private static final String MESSAGE = "요청한 등급이 이 회차에 속하지 않습니다.";

    public PerformanceGradeMismatchException() {
        this(null);
    }

    public PerformanceGradeMismatchException(final Object data) {
        super(BookingErrorCode.E4004, MESSAGE, data);
    }
}
