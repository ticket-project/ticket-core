package com.ticket.booking.hold.domain;

import java.util.List;

import org.springframework.stereotype.Component;

import com.ticket.booking.common.RequestedSeatIds;
import com.ticket.booking.exception.NoAvailableSeatException;
import com.ticket.booking.exception.SeatMismatchInPerformanceException;
import com.ticket.booking.seat.domain.PerformanceSeat;
import com.ticket.booking.seat.domain.PerformanceSeatRepository;
import com.ticket.booking.seat.domain.PerformanceSeatState;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class HoldSeatAvailabilityValidator {
    private final PerformanceSeatRepository performanceSeatRepository;

    public List<PerformanceSeat> validate(
            final Long performanceId, final RequestedSeatIds requestedSeatIds) {
        final List<Long> seatIds = requestedSeatIds.toList();
        final List<PerformanceSeat> performanceSeats =
                performanceSeatRepository.findAllByPerformanceIdAndSeatIdIn(performanceId, seatIds);
        if (performanceSeats.size() != requestedSeatIds.size()) {
            throw new SeatMismatchInPerformanceException(performanceId);
        }

        final boolean hasUnavailableSeat =
                performanceSeats.stream()
                        .anyMatch(seat -> seat.getState() != PerformanceSeatState.AVAILABLE);
        if (hasUnavailableSeat) {
            throw new NoAvailableSeatException(performanceId);
        }
        return performanceSeats;
    }
}
