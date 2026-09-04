package com.ticket.booking.internal.application.performanceseat.event;

import com.ticket.booking.internal.application.performanceseat.event.SeatStatusEvent.SeatStatusAction;

public interface SeatStatusEventPublisher {

    /**
     * @param performanceSeatId 외부 판매 좌석 식별자(물리 {@code seatId}가 아니다).
     */
    void publish(Long performanceId, Long performanceSeatId, SeatStatusAction action);
}
