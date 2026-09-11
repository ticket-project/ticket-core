package com.ticket.booking.exception;

import lombok.Getter;

/**
 * 판매 좌석 편성 요청의 좌석이 그 회차의 Venue에 속하지 않는다.
 *
 * <p>{@code performanceId}·{@code seatId}는 진단 정보다. 공개 {@code error.data}에는 싣지 않는다.
 */
@Getter
public class SeatVenueMismatchException extends BookingException {

    private static final String MESSAGE = "요청한 좌석이 이 회차의 공연장에 속하지 않습니다.";

    private final Long performanceId;
    private final Long seatId;

    public SeatVenueMismatchException(final Long performanceId, final Long seatId) {
        super(BookingErrorCode.E4003, MESSAGE, null);
        this.performanceId = performanceId;
        this.seatId = seatId;
    }
}
