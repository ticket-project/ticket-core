package com.ticket.booking.exception;

import lombok.Getter;

/**
 * 요청한 좌석 중 판매 가능한 것이 없다.
 *
 * <p>{@code performanceId}는 진단 정보다. 단일 좌석 검증과 여러 좌석 검증이 함께 쓰는 예외라
 * 모든 발생 경로가 공유하는 회차만 받는다 — 실패한 좌석 목록은 담지 않는다.
 */
@Getter
public class NoAvailableSeatException extends BookingException {

    private static final String MESSAGE = "이용 가능한 좌석이 없습니다.";

    private final Long performanceId;

    public NoAvailableSeatException(final Long performanceId) {
        super(BookingErrorCode.E3003, MESSAGE, null);
        this.performanceId = performanceId;
    }
}
