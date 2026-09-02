package com.ticket.booking.internal.application.performanceseat.query.model;

import com.ticket.booking.internal.domain.performanceseat.model.PerformanceSeatState;

public record AvailableSeatRow(Long seatId, PerformanceSeatState state, String gradeName, int sortOrder) {}
