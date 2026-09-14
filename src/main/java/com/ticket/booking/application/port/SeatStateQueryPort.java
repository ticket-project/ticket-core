package com.ticket.booking.application.port;

import java.util.List;

import com.ticket.booking.application.SeatStateSnapshotRow;

public interface SeatStateQueryPort {
    List<SeatStateSnapshotRow> findSeatStates(Long performanceId);
}
