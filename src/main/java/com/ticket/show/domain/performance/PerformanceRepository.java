package com.ticket.show.domain.performance;

import java.util.List;
import java.util.Optional;

import com.ticket.show.api.PerformanceSaleSnapshot.GradeInfo;
import com.ticket.show.api.PerformanceVenueLayout.GradeLayout;

/**
 * 회차 aggregate의 복원과 회차 표시값 조회를 담당하는 도메인 Repository다.
 *
 * <p>조회 결과가 없다는 사실만 알려 주고, 그것을 어떤 오류로 볼지는 호출하는 유스케이스가 정한다. 맥락에 따라 인증 실패일 수도, not-found일 수도, 멱등 성공일
 * 수도 있다.
 *
 * <p>표시값 조회가 돌려주는 {@code GradeInfo}·{@code GradeLayout}은 show module의 공개 계약({@code show.api})이다 —
 * 엔티티로 표현되지 않는 복합 join 결과라 projection으로 둔다({@code docs/readability-guidelines.md} §10-1).
 */
public interface PerformanceRepository {
    List<Performance> findAllByShowIdOrderByStartTimeAscPerformanceNoAsc(Long showId);

    Optional<Performance> findById(Long performanceId);

    /** 판매 snapshot과 회차 요약이 함께 쓰는 회차 표시값이다. */
    Optional<PerformanceSaleContext> findSaleContext(long performanceId);

    Optional<PerformanceVenueLayoutContext> findVenueLayoutContext(long performanceId);

    /** 대표 회차는 이 show에서 가장 먼저 만들어진 회차다. */
    Optional<Long> findRepresentativePerformanceIdByShowId(long showId);

    /** 이 회차에 배정된 모든 PerformanceGrade를 반환한다. */
    List<GradeInfo> findPerformanceGrades(long performanceId);

    /** 이 회차에 배정된 모든 PerformanceGrade의 표시값을 반환한다. 가격은 담지 않는다. */
    List<GradeLayout> findGradeLayouts(long performanceId);
}
