package com.ticket.booking.exception;

import lombok.Getter;

/**
 * 본인이 선택하지 않은 좌석을 해제하려 했다.
 *
 * <p>{@code memberId}는 해제를 <b>요청한</b> 회원이다. 실제 점유 회원은 담지 않는다 — 그 값을
 * 위해 저장소를 다시 조회하지 않고, 남의 점유 사실을 응답에 흘리지도 않는다. 세 값 모두
 * 진단 정보이고 공개 {@code error.data}에는 싣지 않는다.
 */
@Getter
public class SeatNotOwnedException extends BookingException {

    private static final String MESSAGE = "본인이 선택한 좌석만 해제할 수 있습니다.";

    private final Long performanceId;
    private final Long seatId;
    private final Long memberId;

    public SeatNotOwnedException(final Long performanceId, final Long seatId, final Long memberId) {
        super(BookingErrorCode.E4002, MESSAGE, null);
        this.performanceId = performanceId;
        this.seatId = seatId;
        this.memberId = memberId;
    }
}
