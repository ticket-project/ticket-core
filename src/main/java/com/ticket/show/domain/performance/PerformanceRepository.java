package com.ticket.show.domain.performance;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * 회차 aggregate의 복원을 담당하는 도메인 Repository다. 구현은 Spring Data JPA가 만든다.
 *
 * <p>Show는 Performance와 다른 aggregate라 {@code showId} scalar로만 연결된다 — 여기서 join하지 않는다.
 *
 * <p>조회 결과가 없다는 사실만 알려 주고, 그것을 어떤 오류로 볼지는 호출하는 유스케이스가 정한다. 맥락에 따라 인증 실패일 수도, not-found일 수도, 멱등 성공일 수도 있다.
 */
public interface PerformanceRepository extends Repository<Performance, Long> {
    List<Performance> findAllByShowIdOrderByStartTimeAscPerformanceNoAsc(Long showId);

    Optional<Performance> findById(Long performanceId);

    /** 대표 회차는 이 show에서 가장 먼저 만들어진(ID가 가장 작은) 회차다. */
    @Query("SELECT MIN(p.id) FROM Performance p WHERE p.showId = :showId")
    Optional<Long> findRepresentativePerformanceIdByShowId(@Param("showId") long showId);

    /** ID가 가장 작은 회차의 등급을 표시 순서대로 반환한다. */
    @Query("""
            SELECT pg FROM PerformanceGrade pg
            WHERE pg.performance.id = (SELECT MIN(p.id) FROM Performance p WHERE p.showId = :showId)
            ORDER BY pg.sortOrder ASC
            """)
    List<PerformanceGrade> findRepresentativePerformanceGrades(@Param("showId") Long showId);

    /**
     * 이 회차에 배정된 모든 PerformanceGrade를 반환한다.
     *
     * <p>등급 코드·이름은 담기지 않는다 — Grade는 다른 aggregate라 여기서 join하지 않고 {@code GradeRepository}로 따로 읽어 use case가 조합한다.
     */
    @Query("SELECT pg FROM PerformanceGrade pg WHERE pg.performance.id = :performanceId")
    List<PerformanceGrade> findPerformanceGrades(@Param("performanceId") long performanceId);
}
