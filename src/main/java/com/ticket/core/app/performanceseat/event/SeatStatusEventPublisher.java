package com.ticket.core.app.performanceseat.event;

import com.ticket.core.app.performanceseat.event.SeatStatusEvent.SeatStatusAction;

public interface SeatStatusEventPublisher {

    void publish(Long performanceId, Long seatId, SeatStatusAction action);
}
