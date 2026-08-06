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

    public boolean isOverCount(final long requestedSeatCount) {
        return maxCanHoldCount != null && requestedSeatCount > maxCanHoldCount;
    }

    public boolean requiresQueueAt(final LocalDateTime now) {
        if (queueMode == null || queueMode == QueueMode.FORCE_OFF) {
            return false;
        }
        if (queueMode == QueueMode.FORCE_ON) {
            return true;
        }
        if (preopenQueueStartAt == null || now == null || now.isBefore(preopenQueueStartAt)) {
            return false;
        }
        return orderCloseTime == null || !now.isAfter(orderCloseTime);
    }
}
