package com.ticket.core.app.performanceseat.event;

import java.time.LocalDateTime;

public record SeatStatusEvent(
        Long performanceId,
        Long seatId,
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
            final Long seatId,
            final SeatStatusAction action,
            final LocalDateTime timestamp
    ) {
        return new SeatStatusEvent(performanceId, seatId, action, timestamp);
    }
}
