package com.ticket.booking.domain;

import com.ticket.booking.domain.PerformanceSeat;
import com.ticket.booking.domain.PerformanceSeatRepository;
import com.ticket.booking.domain.PerformanceSeatState;
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
