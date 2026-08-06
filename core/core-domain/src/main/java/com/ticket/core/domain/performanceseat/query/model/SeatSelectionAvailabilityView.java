package com.ticket.core.domain.performanceseat.query.model;

import com.ticket.core.domain.performanceseat.model.PerformanceSeatState;

import java.time.LocalDateTime;

public record SeatSelectionAvailabilityView(
        LocalDateTime orderOpenTime,
        LocalDateTime orderCloseTime,
        Long performanceSeatId,
        PerformanceSeatState state
) {
}
