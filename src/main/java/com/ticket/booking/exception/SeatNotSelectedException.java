package com.ticket.booking.exception;

/**
 * 본인이 선택하지 않은 좌석으로 예매를 시작하려 했다. 선택한 적이 없거나, 다른 회원이 선택한 좌석이다. 선택 시간이 지나 풀린 좌석은 {@link SeatSelectionExpiredException}이다.
 *
 * <p>선택한 적 없는 좌석과 남이 선택한 좌석을 구분하지 않는다 — 남의 선택 사실을 응답에 흘리지 않는다.
 */
public final class SeatNotSelectedException extends BookingException {
    private static final String MESSAGE = "선택한 좌석만 예매할 수 있습니다.";

    public SeatNotSelectedException() {
        super(BookingErrorCode.E4006, MESSAGE, null);
    }
}
