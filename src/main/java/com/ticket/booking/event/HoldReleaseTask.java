package com.ticket.booking.event;

import java.util.List;
import java.util.Map;

/** hold 해제 후처리의 입력이다. */
public record HoldReleaseTask(
        Long performanceId, String holdKey, List<Long> seatIds, Map<Long, Long> performanceSeatIdBySeatId) {
    public HoldReleaseTask {
        seatIds = List.copyOf(seatIds);
        performanceSeatIdBySeatId = Map.copyOf(performanceSeatIdBySeatId);
    }
}
