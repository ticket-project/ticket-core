package com.ticket.booking.seat.application;

import com.ticket.booking.seat.domain.PerformanceSeatState;

/**
 * 좌석 상태 API 응답 전용 enum.
 * DB 저장용 PerformanceSeatState와 분리하여, 클라이언트에는 단순한 2가지 상태만 노출한다.
 */
public enum SeatStatus {
    AVAILABLE("선택 가능"),
    OCCUPIED("사용 중");

    private final String description;

    SeatStatus(final String description) {
        this.description = description;
    }

    public static SeatStatus from(final PerformanceSeatState state) {
        return state == PerformanceSeatState.AVAILABLE ? AVAILABLE : OCCUPIED;
    }

    public String getDescription() {
        return description;
    }
}
