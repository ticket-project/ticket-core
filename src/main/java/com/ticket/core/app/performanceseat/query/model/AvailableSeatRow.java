package com.ticket.core.app.performanceseat.query.model;

import com.ticket.core.domain.performanceseat.model.PerformanceSeatState;

public record AvailableSeatRow(Long seatId, PerformanceSeatState state, String gradeName, int sortOrder) {}
