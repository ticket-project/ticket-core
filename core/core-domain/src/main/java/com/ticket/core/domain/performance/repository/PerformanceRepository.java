package com.ticket.core.domain.performance.repository;

import com.ticket.core.domain.error.DomainErrorType;
import com.ticket.core.domain.performance.model.Performance;
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
     * 대기열 정책까지 적재한 회차를 반환하고, 없으면 도메인 오류를 던진다.
     */
    default Performance getWithQueuePolicyById(final Long performanceId) {
        return findWithQueuePolicyById(performanceId)
                .orElseThrow(() -> new CoreException(DomainErrorType.DATA_NOT_FOUND,
                        "공연을 찾을 수 없습니다. id=" + performanceId));
    }
}
