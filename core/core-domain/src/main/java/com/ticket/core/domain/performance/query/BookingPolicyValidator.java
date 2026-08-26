package com.ticket.core.domain.performance.query;

import com.ticket.support.error.CoreException;
import com.ticket.core.domain.error.DomainErrorType;
import com.ticket.core.domain.performance.QueueActivation;
import com.ticket.core.domain.performance.query.model.PerformanceBookingPolicyView;

import java.time.LocalDateTime;

/**
 * 회차 예매 정책을 판정한다. 정책을 한 번 조회해 오픈·마감, 좌석 수 한도, 대기열 필요 여부를 모두 답한다.
 */
public final class BookingPolicyValidator {

    private BookingPolicyValidator() {
    }

    /**
     * 예매 가능 시각 안인지 확인한다. 조회 시점이 아니라 판정 시점의 시각으로 비교하므로
     * 정책 값을 캐시해도 오픈·마감 판정은 항상 현재 시각을 따른다.
     */
    public static void ensureBookingOpen(final PerformanceBookingPolicyView policy, final LocalDateTime now) {
        if (policy.orderOpenTime() == null || now.isBefore(policy.orderOpenTime())) {
            throw new CoreException(DomainErrorType.NOT_YET_RESERVE_TIME);
        }
        if (policy.orderCloseTime() == null || now.isAfter(policy.orderCloseTime())) {
            throw new CoreException(DomainErrorType.PERFORMANCE_IS_PAST);
        }
    }

    /**
     * 한도가 없는 회차는 좌석 수를 제한하지 않는다.
     */
    public static void ensureWithinHoldLimit(
            final PerformanceBookingPolicyView policy,
            final long requestedSeatCount
    ) {
        if (policy.maxCanHoldCount() == null) {
            return;
        }
        if (requestedSeatCount > policy.maxCanHoldCount()) {
            throw new CoreException(DomainErrorType.EXCEED_HOLD_LIMIT);
        }
    }

    public static boolean requiresQueue(final PerformanceBookingPolicyView policy, final LocalDateTime now) {
        return QueueActivation.isRequiredAt(
                policy.queueMode(),
                policy.preopenQueueStartAt(),
                now,
                policy.orderCloseTime()
        );
    }
}
