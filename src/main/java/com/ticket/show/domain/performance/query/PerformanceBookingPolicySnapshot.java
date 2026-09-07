package com.ticket.show.domain.performance.query;

import com.ticket.show.domain.queue.QueueLevel;
import com.ticket.show.domain.queue.QueueMode;

import java.time.LocalDateTime;

public record PerformanceBookingPolicySnapshot(
        Long performanceId,
        Long showId,
        LocalDateTime orderOpenTime,
        LocalDateTime orderCloseTime,
        Integer maxCanHoldCount,
        Integer holdTime,
        QueueMode queueMode,
        QueueLevel queueLevel,
        LocalDateTime preopenQueueStartAt,
        String waitingRoomMessage,
        String reason
) {
}
