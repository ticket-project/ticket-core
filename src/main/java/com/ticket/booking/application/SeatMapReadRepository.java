package com.ticket.booking.application;

import com.ticket.booking.application.SeatStateSnapshotRow;

import java.util.List;

public interface SeatMapReadRepository {

    List<SeatStateSnapshotRow> findSeatStatuses(Long performanceId);
}
