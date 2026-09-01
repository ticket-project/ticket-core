package com.ticket.core.domain.performanceseat.command;

import com.ticket.core.domain.performanceseat.model.PerformanceSeat;
import com.ticket.core.domain.performanceseat.repository.PerformanceSeatRepository;
import com.ticket.core.domain.performanceseat.model.PerformanceSeatState;
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
