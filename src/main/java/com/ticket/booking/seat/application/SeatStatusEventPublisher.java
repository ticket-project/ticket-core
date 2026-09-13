package com.ticket.booking.seat.application;

import com.ticket.booking.seat.application.SeatStatusEvent.SeatStatusAction;

public interface SeatStatusEventPublisher {
    void publish(Long performanceId, Long performanceSeatId, Long seatId, SeatStatusAction action);
}
