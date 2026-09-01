package com.ticket.core.domain.order.command.create;

import com.ticket.core.domain.hold.model.Hold;
import com.ticket.core.domain.performanceseat.model.PerformanceSeat;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 생성된 hold와 그 hold가 잡은 좌석이다.
 */
public record HoldAllocation(
        Hold hold,
        List<PerformanceSeat> performanceSeats
) {

    public String holdKey() {
        return hold.holdKey();
    }

    public LocalDateTime expiresAt() {
        return hold.expiresAt();
    }

    public LocalDateTime startedAt(final Duration holdDuration) {
        return hold.startedAt(holdDuration);
    }
}
