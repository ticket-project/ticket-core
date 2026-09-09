package com.ticket.booking.application;

import com.ticket.booking.application.port.SeatMapQueryPort;

import com.ticket.booking.application.SeatStateSnapshotRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
public class SeatStateSnapshotReader {

    private final SeatMapQueryPort seatMapQueryPort;

    @Transactional(readOnly = true)
    public List<SeatStateSnapshotRow> read(final Long performanceId) {
        return seatMapQueryPort.findSeatStatuses(performanceId);
    }
}
