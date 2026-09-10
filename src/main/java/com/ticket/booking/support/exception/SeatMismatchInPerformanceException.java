package com.ticket.booking.support.exception;

import lombok.Getter;

/**
 * 요청한 좌석이 그 회차의 좌석이 아니다.
 *
 * <p>{@code performanceId}는 진단 정보다. 어느 좌석이 없는지 찾기 위한 추가 조회는 하지 않는다.
 */
@Getter
public class SeatMismatchInPerformanceException extends BookingException {

    private static final String MESSAGE = "요청한 좌석 정보와 일치하지 않습니다.";

    private final Long performanceId;

    public SeatMismatchInPerformanceException(final Long performanceId) {
        super(BookingErrorCode.E4000, MESSAGE, null);
        this.performanceId = performanceId;
    }
}
