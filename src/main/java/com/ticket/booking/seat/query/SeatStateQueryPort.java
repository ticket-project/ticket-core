package com.ticket.booking.seat.query;

import java.util.List;

public interface SeatStateQueryPort {
    List<SeatStateSnapshotRow> findSeatStates(Long performanceId);
}
