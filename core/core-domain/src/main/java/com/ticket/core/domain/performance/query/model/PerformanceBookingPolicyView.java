package com.ticket.core.domain.performance.query.model;

import com.ticket.core.domain.queue.model.QueueLevel;
import com.ticket.core.domain.queue.model.QueueMode;
import com.ticket.core.support.exception.CoreException;
import com.ticket.core.support.exception.ErrorType;

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

    /**
     * 예매 가능 시각 안인지 확인한다. 조회 시점이 아니라 판정 시점의 시각으로 비교하므로
     * 정책 값을 캐시해도 오픈·마감 판정은 항상 현재 시각을 따른다.
     */
    public void ensureBookingOpenAt(final LocalDateTime now) {
        if (orderOpenTime == null || now.isBefore(orderOpenTime)) {
            throw new CoreException(ErrorType.NOT_YET_RESERVE_TIME);
        }
        if (orderCloseTime == null || now.isAfter(orderCloseTime)) {
            throw new CoreException(ErrorType.PERFORMANCE_IS_PAST);
        }
    }

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
