package com.ticket.core.domain.performance.repository;

import com.ticket.core.domain.error.DomainErrorType;
import com.ticket.core.domain.performance.model.Performance;
import com.ticket.core.domain.performance.query.model.PerformanceBookingPolicyView;
import com.ticket.support.error.CoreException;

import java.util.List;
import java.util.Optional;

/**
 * 회차 aggregate의 복원을 담당하는 도메인 Repository다.
 */
public interface PerformanceRepository {

    List<Performance> findAllByShowIdOrderByStartTimeAscPerformanceNoAsc(Long showId);

    /**
     * 대기열 정책까지 함께 적재한 회차를 반환한다. 정책 판정 경로에서 추가 조회가 나지 않게 한다.
     */
    Optional<Performance> findWithQueuePolicyById(Long performanceId);

    /**
     * 예매 정책 판정에 필요한 값만 모아 반환한다.
     *
     * <p>불변식 판정 경로가 회차 엔티티 전체를 적재하지 않도록 도메인 값으로 좁혀 조회한다.
     */
    Optional<PerformanceBookingPolicyView> findBookingPolicyById(Long performanceId);

    /**
     * 예매 정책을 반환하고, 없으면 도메인 오류를 던진다.
     */
    default PerformanceBookingPolicyView getBookingPolicyById(final Long performanceId) {
        return findBookingPolicyById(performanceId)
                .orElseThrow(() -> new CoreException(DomainErrorType.DATA_NOT_FOUND,
                        "공연을 찾을 수 없습니다. id=" + performanceId));
    }

    /**
     * 대기열 정책까지 적재한 회차를 반환하고, 없으면 도메인 오류를 던진다.
     */
    default Performance getWithQueuePolicyById(final Long performanceId) {
        return findWithQueuePolicyById(performanceId)
                .orElseThrow(() -> new CoreException(DomainErrorType.DATA_NOT_FOUND,
                        "공연을 찾을 수 없습니다. id=" + performanceId));
    }
}
