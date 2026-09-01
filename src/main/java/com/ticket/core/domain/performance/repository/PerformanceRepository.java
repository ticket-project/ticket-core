package com.ticket.core.domain.performance.repository;

import com.ticket.core.domain.performance.model.Performance;
import com.ticket.core.domain.performance.query.model.PerformanceBookingPolicySnapshot;

import java.util.List;
import java.util.Optional;

/**
 * 회차 aggregate의 복원을 담당하는 도메인 Repository다.
 *
 * <p>조회 결과가 없다는 사실만 알려 주고, 그것을 어떤 오류로 볼지는 호출하는 유스케이스가 정한다.
 * 맥락에 따라 인증 실패일 수도, not-found일 수도, 멱등 성공일 수도 있다.
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
    Optional<PerformanceBookingPolicySnapshot> findBookingPolicyById(Long performanceId);
}
