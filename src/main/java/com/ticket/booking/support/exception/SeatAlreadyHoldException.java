package com.ticket.booking.support.exception;

import lombok.Getter;

/**
 * 다른 회원이 이미 선점(hold)한 좌석이다.
 *
 * <p>{@code performanceId}·{@code seatId}는 진단 정보다. 공개 {@code error.data}에는 싣지 않는다.
 */
@Getter
public class SeatAlreadyHoldException extends BookingException {

    private static final String MESSAGE = "좌석이 이미 선점되었습니다.";

    private final Long performanceId;
    private final Long seatId;

    public SeatAlreadyHoldException(final Long performanceId, final Long seatId) {
        super(BookingErrorCode.E6000, MESSAGE, null);
        this.performanceId = performanceId;
        this.seatId = seatId;
    }
}
