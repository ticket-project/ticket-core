package com.ticket.booking.application;

import java.time.LocalDateTime;

import org.jspecify.annotations.Nullable;

/**
 * 좌석 상태 WebSocket payload다. {@code seatId}는 기존 프론트가 좌석 배치와 상태를 연결하는 물리 좌석 식별자이고, {@code
 * performanceSeatId}는 회차 판매 좌석 식별자다.
 */
public record SeatStatusEvent(
        Long performanceId,
        @Nullable Long performanceSeatId,
        Long seatId,
        SeatStatusAction action,
        LocalDateTime timestamp) {
    public enum SeatStatusAction {
        SELECTED,
        DESELECTED,
        HELD,
        RELEASED,
        RESERVED
    }

    public static SeatStatusEvent of(
            final Long performanceId,
            final @Nullable Long performanceSeatId,
            final Long seatId,
            final SeatStatusAction action,
            final LocalDateTime timestamp) {
        return new SeatStatusEvent(performanceId, performanceSeatId, seatId, action, timestamp);
    }
}
