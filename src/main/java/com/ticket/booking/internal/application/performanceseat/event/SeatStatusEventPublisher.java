package com.ticket.booking.internal.application.performanceseat.event;

import com.ticket.booking.internal.application.performanceseat.event.SeatStatusEvent.SeatStatusAction;

public interface SeatStatusEventPublisher {

    void publish(Long performanceId, Long seatId, SeatStatusAction action);
}
