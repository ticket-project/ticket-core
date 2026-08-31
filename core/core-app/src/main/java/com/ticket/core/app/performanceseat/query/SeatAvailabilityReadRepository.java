package com.ticket.core.app.performanceseat.query;

import com.ticket.core.app.performanceseat.query.model.AvailableSeatRow;

import java.util.List;

public interface SeatAvailabilityReadRepository {

    List<AvailableSeatRow> findAvailableSeatRows(Long performanceId, Long showId);
}
