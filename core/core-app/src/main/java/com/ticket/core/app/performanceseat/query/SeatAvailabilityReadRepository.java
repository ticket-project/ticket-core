package com.ticket.core.app.performanceseat.query;

import com.ticket.core.app.performanceseat.query.SeatAvailabilityCalculator;

import java.util.List;

public interface SeatAvailabilityReadRepository {

    List<SeatAvailabilityCalculator.AvailableSeatRow> findAvailableSeatRows(Long performanceId, Long showId);
}
