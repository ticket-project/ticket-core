package com.ticket.booking.application;

import com.ticket.booking.application.SeatStatusEvent.SeatStatusAction;

public interface SeatStatusEventPublisher {
    void publish(Long performanceId, Long performanceSeatId, Long seatId, SeatStatusAction action);
}
