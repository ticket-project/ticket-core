package com.ticket.booking.exception;

import lombok.Getter;

/**
 * 같은 회원에게 이미 진행 중인 결제 대기 주문이 있다.
 *
 * <p>두 값은 중복 여부를 판정한 exists 조회가 이미 쓴 값이다. 진단 정보이고 공개
 * {@code error.data}에는 싣지 않는다.
 */
@Getter
public class PendingOrderAlreadyExistsException extends BookingException {

    private static final String MESSAGE = "이미 진행 중인 결제 대기 주문이 있습니다.";

    private final Long memberId;
    private final Long performanceId;

    public PendingOrderAlreadyExistsException(final Long memberId, final Long performanceId) {
        super(BookingErrorCode.E5004, MESSAGE, null);
        this.memberId = memberId;
        this.performanceId = performanceId;
    }
}
