package com.ticket.core.domain.performanceseat.query;

import com.ticket.core.domain.performanceseat.query.model.SeatStateView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
public class SeatStatusDbReader {

    private final SeatMapQueryRepository seatMapQueryRepository;

    @Transactional(readOnly = true)
    public List<SeatStateView> read(final Long performanceId) {
        return seatMapQueryRepository.findSeatStatuses(performanceId);
    }
}
