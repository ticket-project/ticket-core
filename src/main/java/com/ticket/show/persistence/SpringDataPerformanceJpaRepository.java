package com.ticket.show.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ticket.show.domain.performance.Performance;
import com.ticket.show.domain.performance.PerformanceSaleContext;
import com.ticket.show.domain.performance.PerformanceVenueLayoutContext;
import com.ticket.show.usecase.view.PerformanceSummaryView;

/**
 * Show는 Performance와 다른 aggregate라 {@code showId} scalar로만 연결된다 — 아래 조회들은 연관관계 경로 탐색 대신 명시적 join
 * JPQL을 쓴다.
 */
interface SpringDataPerformanceJpaRepository extends JpaRepository<Performance, Long> {
    List<Performance> findAllByShowIdOrderByStartTimeAscPerformanceNoAsc(Long showId);

    @Query(
            """
            SELECT new com.ticket.show.usecase.view.PerformanceSummaryView(s.title, s.venueId, p.startTime)
            FROM Performance p
            JOIN Show s ON s.id = p.showId
            WHERE p.id = :performanceId
            """)
    Optional<PerformanceSummaryView> findSummaryByPerformanceId(
            @Param("performanceId") Long performanceId);

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

    /** 대표 회차는 이 show에서 가장 먼저 만들어진 회차다({@code ShowQueryRepository}의 대표 등급 조회와 같은 기준). */
    @Query("SELECT MIN(p.id) FROM Performance p WHERE p.showId = :showId")
    Optional<Long> findRepresentativePerformanceIdByShowId(@Param("showId") long showId);
}
