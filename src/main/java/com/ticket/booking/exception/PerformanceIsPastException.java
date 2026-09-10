package com.ticket.booking.exception;

import lombok.Getter;

/**
 * 예매 마감 시각을 지난 회차다.
 *
 * <p>{@code performanceId}는 진단 정보다. 공개 {@code error.data}에는 싣지 않는다.
 */
@Getter
public class PerformanceIsPastException extends BookingException {

    private static final String MESSAGE = "과거 공연은 예매할 수 없습니다.";

    private final Long performanceId;

    public PerformanceIsPastException(final Long performanceId) {
        super(BookingErrorCode.E3001, MESSAGE, null);
        this.performanceId = performanceId;
    }
}
