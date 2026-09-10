package com.ticket.booking.seat.application.port;

import com.ticket.booking.seat.application.SeatStateSnapshotRow;

import java.util.List;

public interface SeatMapQueryPort {

    List<SeatStateSnapshotRow> findSeatStatuses(Long performanceId);
}
