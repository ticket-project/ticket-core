package com.ticket.booking.seat.domain;

import java.util.List;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AvailablePerformanceSeatReader {
    private final PerformanceSeatRepository performanceSeatRepository;

    public List<PerformanceSeat> readAllAvailable() {
        return performanceSeatRepository.findAllByStateEquals(PerformanceSeatState.AVAILABLE);
    }
}
