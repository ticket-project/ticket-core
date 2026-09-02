package com.ticket.catalog.internal.domain.performance.query;

import com.ticket.catalog.internal.domain.queue.QueueLevel;
import com.ticket.catalog.internal.domain.queue.QueueMode;

import java.time.LocalDateTime;

public record PerformanceBookingPolicySnapshot(
        Long performanceId,
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
