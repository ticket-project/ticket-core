package com.ticket.booking.internal.application.performanceseat.event;

import java.time.LocalDateTime;

/**
 * 좌석 상태 WebSocket payload다. 외부 판매 좌석 식별자는 물리 {@code seatId}가 아니라
 * {@code performanceSeatId}다 — 좌석 상태·잔여석 API(Task 10)와 같은 식별자를 쓴다.
 */
public record SeatStatusEvent(
        Long performanceId,
        Long performanceSeatId,
        SeatStatusAction action,
        LocalDateTime timestamp
) {

    public enum SeatStatusAction {
        SELECTED,
        DESELECTED,
        HELD,
        RELEASED,
        RESERVED
    }

    public static SeatStatusEvent of(
            final Long performanceId,
            final Long performanceSeatId,
            final SeatStatusAction action,
            final LocalDateTime timestamp
    ) {
        return new SeatStatusEvent(performanceId, performanceSeatId, action, timestamp);
    }
}
