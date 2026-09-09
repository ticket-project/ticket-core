package com.ticket.booking.application.port;

import com.ticket.booking.application.SeatStateSnapshotRow;

import java.util.List;

public interface SeatMapQueryPort {

    List<SeatStateSnapshotRow> findSeatStatuses(Long performanceId);
}
