package com.ticket.show.domain.performance.repository;

import com.ticket.show.domain.performance.Performance;

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

    Optional<Performance> findById(Long performanceId);
}
