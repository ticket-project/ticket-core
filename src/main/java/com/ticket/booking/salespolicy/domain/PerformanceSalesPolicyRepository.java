package com.ticket.booking.salespolicy.domain;

import com.ticket.booking.salespolicy.domain.PerformanceSalesPolicy;

import java.util.Optional;

/**
 * aggregate의 저장과 복원을 담당하는 도메인 Repository다. 조회 결과가 없다는 사실만 알려 주고,
 * 그것을 어떤 오류로 볼지는 호출하는 유스케이스가 정한다.
 */
public interface PerformanceSalesPolicyRepository {

    Optional<PerformanceSalesPolicy> findById(Long performanceId);
}
