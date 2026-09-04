package com.ticket.booking.internal.application.performanceseat.query;

import com.ticket.booking.internal.application.performanceseat.query.model.SeatStateSnapshotRow;

import java.util.List;

public interface SeatMapReadRepository {

    List<SeatStateSnapshotRow> findSeatStatuses(Long performanceId);
}
