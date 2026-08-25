package com.ticket.core.domain.hold.command;

import com.ticket.core.domain.order.command.create.RequestedSeatIds;
import com.ticket.core.domain.performanceseat.repository.PerformanceSeatRepository;
import com.ticket.core.domain.performanceseat.model.PerformanceSeat;
import com.ticket.core.domain.performanceseat.model.PerformanceSeatState;
import com.ticket.support.error.CoreException;
import com.ticket.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class HoldSeatAvailabilityValidator {

    private final PerformanceSeatRepository performanceSeatRepository;

    public List<PerformanceSeat> validate(final Long performanceId, final RequestedSeatIds requestedSeatIds) {
        final List<Long> seatIds = requestedSeatIds.toList();
        final List<PerformanceSeat> performanceSeats = performanceSeatRepository.findAllByPerformanceIdAndSeatIdIn(performanceId, seatIds);
        if (performanceSeats.size() != requestedSeatIds.size()) {
            throw new CoreException(ErrorType.SEAT_MISMATCH_IN_PERFORMANCE);
        }

        final boolean hasUnavailableSeat = performanceSeats.stream()
                .anyMatch(seat -> seat.getState() != PerformanceSeatState.AVAILABLE);
        if (hasUnavailableSeat) {
            throw new CoreException(ErrorType.NOT_EXIST_AVAILABLE_SEAT);
        }
        return performanceSeats;
    }
}
