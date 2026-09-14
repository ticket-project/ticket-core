package com.ticket.booking.application;

import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.ticket.booking.application.port.SeatStateQueryPort;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class SeatStateSnapshotReader {
    private final SeatStateQueryPort seatStateQueryPort;

    @Transactional(readOnly = true)
    public List<SeatStateSnapshotRow> read(final Long performanceId) {
        return seatStateQueryPort.findSeatStates(performanceId);
    }
}
