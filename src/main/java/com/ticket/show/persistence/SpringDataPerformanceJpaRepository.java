package com.ticket.show.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ticket.show.domain.performance.Performance;
import com.ticket.show.domain.performance.PerformanceGrade;

/** Show는 Performance와 다른 aggregate라 {@code showId} scalar로만 연결된다 — 여기서 join하지 않는다. */
interface SpringDataPerformanceJpaRepository extends JpaRepository<Performance, Long> {
    List<Performance> findAllByShowIdOrderByStartTimeAscPerformanceNoAsc(Long showId);

    /** 대표 회차는 이 show에서 가장 먼저 만들어진 회차다({@code ShowQuerydslRepository}의 대표 등급 조회와 같은 기준). */
    @Query("SELECT MIN(p.id) FROM Performance p WHERE p.showId = :showId")
    Optional<Long> findRepresentativePerformanceIdByShowId(@Param("showId") long showId);

    /**
     * 이 회차에 배정된 PerformanceGrade를 엔티티로 반환한다. 등급 코드·이름은 Grade가 다른 aggregate라 여기서 join하지 않는다 — 호출하는 use case가
     * {@code GradeRepository}로 따로 읽어 조합한다.
     */
    @Query("SELECT pg FROM PerformanceGrade pg WHERE pg.performance.id = :performanceId")
    List<PerformanceGrade> findGradesByPerformanceId(@Param("performanceId") long performanceId);
}
