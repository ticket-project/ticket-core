package com.ticket.booking.exception;

/** 선택해 둔 좌석의 선택 시간이 지나 풀린 뒤 예매를 시작하려 했다. 다시 선택하면 예매할 수 있다. */
public final class SeatSelectionExpiredException extends BookingException {
    private static final String MESSAGE = "좌석 선택 시간이 지났습니다. 좌석을 다시 선택해 주세요.";

    public SeatSelectionExpiredException() {
        super(BookingErrorCode.E4007, MESSAGE, null);
    }
}
