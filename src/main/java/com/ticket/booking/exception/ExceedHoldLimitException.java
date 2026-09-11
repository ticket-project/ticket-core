package com.ticket.booking.exception;

import lombok.Getter;

/**
 * 회차가 정한 1인 선점 좌석 수 한도를 넘었다.
 *
 * <p>{@code requestedSeatCount}·{@code maxSeatCount}는 진단 정보다. 한도 초과 여부는 계속
 * {@code HoldPolicy}가 판단하고 이 생성자는 다시 검증하지 않는다. 한도가 없는 회차(무제한)는
 * 애초에 이 예외를 만들지 않으므로 {@code maxSeatCount}는 여기서 non-null이다.
 */
@Getter
public class ExceedHoldLimitException extends BookingException {

    private static final String MESSAGE = "선점 가능한 좌석 수를 초과하였습니다.";

    private final long requestedSeatCount;
    private final int maxSeatCount;

    public ExceedHoldLimitException(final long requestedSeatCount, final int maxSeatCount) {
        super(BookingErrorCode.E6001, MESSAGE, null);
        this.requestedSeatCount = requestedSeatCount;
        this.maxSeatCount = maxSeatCount;
    }
}
