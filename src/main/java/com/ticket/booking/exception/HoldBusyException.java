package com.ticket.booking.exception;


/**
 * 같은 좌석에 대한 선점 처리가 진행 중이어서 분산락을 얻지 못했다.
 */
public class HoldBusyException extends BookingException {

    /** 분산락 실패 기본 문구로도 쓰여서 노출한다({@code RedissonLockManager}). */
    public static final String MESSAGE = "좌석 선점 처리 중입니다. 잠시 후 다시 시도해주세요.";

    public HoldBusyException() {
        this(null);
    }

    public HoldBusyException(final Object data) {
        super(BookingErrorCode.E6003, MESSAGE, data);
    }
}
