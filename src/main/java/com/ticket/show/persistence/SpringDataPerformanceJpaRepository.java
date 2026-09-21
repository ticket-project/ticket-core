package com.ticket.show.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ticket.show.domain.performance.Performance;
import com.ticket.show.domain.performance.PerformanceGrade;
import com.ticket.show.domain.performance.PerformanceSaleContext;
import com.ticket.show.domain.performance.PerformanceVenueLayoutContext;

/**
 * Show는 Performance와 다른 aggregate라 {@code showId} scalar로만 연결된다 — 아래 조회들은 연관관계 경로 탐색 대신 명시적 join
 * JPQL을 쓴다.
 */
interface SpringDataPerformanceJpaRepository extends JpaRepository<Performance, Long> {
    List<Performance> findAllByShowIdOrderByStartTimeAscPerformanceNoAsc(Long showId);

    @Query(
            """
            SELECT new com.ticket.show.domain.performance.PerformanceSaleContext(
                   p.id, s.id, s.title, s.venueId, p.startTime)
            FROM Performance p
            JOIN Show s ON s.id = p.showId
            WHERE p.id = :performanceId
            """)
    Optional<PerformanceSaleContext> findSaleContextByPerformanceId(
            @Param("performanceId") long performanceId);

    @Query(
            """
            SELECT new com.ticket.show.domain.performance.PerformanceVenueLayoutContext(
                   p.id, s.venueId)
            FROM Performance p
            JOIN Show s ON s.id = p.showId
            WHERE p.id = :performanceId
            """)
    Optional<PerformanceVenueLayoutContext> findVenueLayoutContextByPerformanceId(
            @Param("performanceId") long performanceId);

    /** 대표 회차는 이 show에서 가장 먼저 만들어진 회차다({@code ShowQuerydslRepository}의 대표 등급 조회와 같은 기준). */
    @Query("SELECT MIN(p.id) FROM Performance p WHERE p.showId = :showId")
    Optional<Long> findRepresentativePerformanceIdByShowId(@Param("showId") long showId);

    /**
     * 이 회차에 배정된 PerformanceGrade를 엔티티로 반환한다. 등급 코드·이름은 Grade가 다른 aggregate라 여기서 join하지 않는다 — 호출하는
     * use case가 {@code GradeRepository}로 따로 읽어 조합한다.
     */
    @Query("SELECT pg FROM PerformanceGrade pg WHERE pg.performance.id = :performanceId")
    List<PerformanceGrade> findGradesByPerformanceId(@Param("performanceId") long performanceId);
}
