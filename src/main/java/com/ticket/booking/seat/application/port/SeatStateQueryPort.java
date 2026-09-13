package com.ticket.booking.seat.application.port;

import java.util.List;

import com.ticket.booking.seat.application.SeatStateSnapshotRow;

public interface SeatStateQueryPort {
    List<SeatStateSnapshotRow> findSeatStates(Long performanceId);
}
