package com.ticket.catalog.domain.performance.policy;

import com.ticket.catalog.domain.performance.QueueActivation;
import com.ticket.catalog.domain.performance.query.PerformanceBookingPolicySnapshot;

import java.time.LocalDateTime;

/**
 * 회차 예매 정책 중 catalog가 판정하는 것을 담는다. 현재는 대기열 필요 여부뿐이다.
 *
 * <p>예전에는 오픈·마감({@code ensureBookingOpen})과 좌석 수 한도({@code ensureWithinHoldLimit})
 * 판정도 여기 있었지만 <b>프로덕션에서 호출하는 곳이 없었다</b> — 실제로는 booking의
 * {@code BookingPolicyGuard}가 catalog의 공개 계약 {@code BookingPolicySnapshot}으로 같은 규칙을
 * 다시 계산한다. 두 곳이 같은 규칙을 갖고 있으면 그 규칙이 내는 오류(E3001·E3002·E6001)를 어느
 * module이 소유하는지 정할 수 없어, 호출자가 없는 이쪽을 지웠다.
 */
public final class BookingPolicyValidator {

    private BookingPolicyValidator() {
    }

    public static boolean requiresQueue(final PerformanceBookingPolicySnapshot policy, final LocalDateTime now) {
        return QueueActivation.isRequiredAt(
                policy.queueMode(),
                policy.preopenQueueStartAt(),
                now,
                policy.orderCloseTime()
        );
    }
}
