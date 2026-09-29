package com.ticket.booking.exception;

import lombok.Getter;

/**
 * 본인이 선택하지 않은 좌석으로 예매를 시작하려 했다. 선택하지 않았거나, 선택이 만료됐거나, 다른 회원이 선택한 좌석이다.
 *
 * <p>세 경우를 구분하지 않는다 — 남의 선택 사실을 응답에 흘리지 않는다. 필드는 진단 정보이고 공개 {@code error.data}에는 싣지 않는다.
 */
@Getter
public final class SeatNotSelectedException extends BookingException {
    private static final String MESSAGE = "선택한 좌석만 예매할 수 있습니다.";
    private final Long performanceId;
    private final Long memberId;

    public SeatNotSelectedException(final Long performanceId, final Long memberId) {
        super(BookingErrorCode.E4006, MESSAGE, null);
        this.performanceId = performanceId;
        this.memberId = memberId;
    }
}
