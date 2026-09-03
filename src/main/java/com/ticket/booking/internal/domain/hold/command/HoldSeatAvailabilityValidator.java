package com.ticket.booking.internal.domain.hold.command;

import com.ticket.booking.internal.domain.order.command.create.RequestedSeatIds;
import com.ticket.booking.internal.domain.performanceseat.repository.PerformanceSeatRepository;
import com.ticket.booking.internal.domain.performanceseat.model.PerformanceSeat;
import com.ticket.booking.internal.domain.performanceseat.model.PerformanceSeatState;
import com.ticket.booking.internal.exception.NoAvailableSeatException;
import com.ticket.booking.internal.exception.SeatMismatchInPerformanceException;
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
            throw new SeatMismatchInPerformanceException();
        }

        final boolean hasUnavailableSeat = performanceSeats.stream()
                .anyMatch(seat -> seat.getState() != PerformanceSeatState.AVAILABLE);
        if (hasUnavailableSeat) {
            throw new NoAvailableSeatException();
        }
        return performanceSeats;
    }
}
