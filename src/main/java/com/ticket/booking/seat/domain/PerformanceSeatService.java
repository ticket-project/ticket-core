package com.ticket.booking.seat.domain;

import com.ticket.booking.seat.domain.PerformanceSeat;
import com.ticket.booking.seat.domain.PerformanceSeatRepository;
import com.ticket.booking.seat.domain.PerformanceSeatState;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PerformanceSeatService {
    private final PerformanceSeatRepository performanceSeatRepository;

    public List<PerformanceSeat> getAllAvailableSeats() {
        return performanceSeatRepository.findAllByStateEquals(PerformanceSeatState.AVAILABLE);
//        availableSeats.stream()
    }
}
