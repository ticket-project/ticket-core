package com.ticket.booking.exception;

import java.util.List;

import lombok.Getter;

/**
 * 선택해 둔 좌석의 선택 시간이 지나 풀린 뒤 예매를 시작하려 했다. 다시 선택하면 예매할 수 있다.
 *
 * <p>필드는 진단 정보이고 공개 {@code error.data}에는 싣지 않는다.
 */
@Getter
public final class SeatSelectionExpiredException extends BookingException {
    private static final String MESSAGE = "좌석 선택 시간이 지났습니다. 좌석을 다시 선택해 주세요.";
    private final Long performanceId;
    private final Long memberId;
    private final List<Long> seatIds;

    public SeatSelectionExpiredException(final Long performanceId, final Long memberId, final List<Long> seatIds) {
        super(BookingErrorCode.E4007, MESSAGE, null);
        this.performanceId = performanceId;
        this.memberId = memberId;
        this.seatIds = List.copyOf(seatIds);
    }
}
