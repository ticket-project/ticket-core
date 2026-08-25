package com.ticket.core.domain.performanceseat.query;

import com.ticket.core.domain.performanceseat.query.model.SeatSelectionAvailabilityView;

import java.util.Optional;

public interface SeatSelectionAvailabilityQueryRepository {

    Optional<SeatSelectionAvailabilityView> findSelectableSeat(Long performanceId, Long seatId);
}
