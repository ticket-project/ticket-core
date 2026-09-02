package com.ticket.booking.internal.application.performanceseat.query.model;

public record SeatStateView(
        Long seatId,
        SeatStatus status
) {
}
