package com.ticket.booking.application.performanceseat.query;

import com.ticket.booking.application.performanceseat.query.model.SeatStateSnapshotRow;

import java.util.List;

public interface SeatMapReadRepository {

    List<SeatStateSnapshotRow> findSeatStatuses(Long performanceId);
}
