package com.ticket.core.domain.performance.query.model;

import com.ticket.core.domain.queue.model.QueueLevel;
import com.ticket.core.domain.queue.model.QueueMode;

import java.time.LocalDateTime;

public record PerformanceBookingPolicyView(
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
