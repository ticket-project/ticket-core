package com.ticket.booking.application;

import org.jspecify.annotations.Nullable;

import com.ticket.booking.application.SeatStatusEvent.SeatStatusAction;

public interface SeatStatusEventPublisher {
    // performanceSeatId는 회차 판매 좌석을 찾지 못하면 null이다 — 표시용 값이라 없어도 발행을 막지 않는다.
    void publish(
            Long performanceId,
            @Nullable Long performanceSeatId,
            Long seatId,
            SeatStatusAction action);
}
