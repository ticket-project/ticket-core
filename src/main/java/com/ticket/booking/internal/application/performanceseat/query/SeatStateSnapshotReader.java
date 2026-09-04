package com.ticket.booking.internal.application.performanceseat.query;

import com.ticket.booking.internal.application.performanceseat.query.model.SeatStateSnapshotRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
public class SeatStateSnapshotReader {

    private final SeatMapReadRepository seatMapReadRepository;

    @Transactional(readOnly = true)
    public List<SeatStateSnapshotRow> read(final Long performanceId) {
        return seatMapReadRepository.findSeatStatuses(performanceId);
    }
}
