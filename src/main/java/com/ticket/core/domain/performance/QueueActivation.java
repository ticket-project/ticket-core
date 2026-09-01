package com.ticket.core.domain.performance;

import com.ticket.core.domain.queue.model.QueueMode;

import java.time.LocalDateTime;

/**
 * 대기열을 태워야 하는 시각인지 판정한다.
 *
 * <p>회차 정책을 엔티티로 든 경로와 프로젝션으로 든 경로가 같은 규칙을 따라야 하므로
 * 어느 타입에도 매이지 않게 값만 받는다.
 */
public final class QueueActivation {

    private QueueActivation() {
    }

    public static boolean isRequiredAt(
            final QueueMode queueMode,
            final LocalDateTime preopenQueueStartAt,
            final LocalDateTime now,
            final LocalDateTime orderCloseTime
    ) {
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
