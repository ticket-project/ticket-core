package com.ticket.show.domain.performance;

import java.util.List;
import java.util.Optional;

/**
 * 회차 aggregate의 복원과 회차 표시값 조회를 담당하는 도메인 Repository다.
 *
 * <p>조회 결과가 없다는 사실만 알려 주고, 그것을 어떤 오류로 볼지는 호출하는 유스케이스가 정한다. 맥락에 따라 인증 실패일 수도, not-found일 수도, 멱등 성공일
 * 수도 있다.
 */
public interface PerformanceRepository {
    List<Performance> findAllByShowIdOrderByStartTimeAscPerformanceNoAsc(Long showId);

    Optional<Performance> findById(Long performanceId);

    /** 판매 snapshot과 회차 요약이 함께 쓰는 회차 표시값이다. */
    Optional<PerformanceSaleContext> findSaleContext(long performanceId);

    Optional<PerformanceVenueLayoutContext> findVenueLayoutContext(long performanceId);

    /** 대표 회차는 이 show에서 가장 먼저 만들어진 회차다. */
    Optional<Long> findRepresentativePerformanceIdByShowId(long showId);

    /**
     * 이 회차에 배정된 모든 PerformanceGrade를 반환한다.
     *
     * <p>등급 코드·이름은 담기지 않는다 — Grade는 다른 aggregate라 {@code GradeRepository}로 따로 읽어 use case가 조합한다.
     * 조합할 때 Grade를 찾지 못한 편성은 제외한다.
     */
    List<PerformanceGrade> findPerformanceGrades(long performanceId);
}
