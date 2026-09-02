package com.ticket.booking.internal.application.performanceseat.query;

import com.ticket.booking.internal.application.performanceseat.query.model.AvailableSeatRow;

import java.util.List;

public interface SeatAvailabilityReadRepository {

    List<AvailableSeatRow> findAvailableSeatRows(Long performanceId, Long showId);
}
