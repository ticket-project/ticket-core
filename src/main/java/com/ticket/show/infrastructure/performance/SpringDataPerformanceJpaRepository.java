package com.ticket.show.infrastructure.performance;

import com.ticket.show.domain.performance.Performance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface SpringDataPerformanceJpaRepository extends JpaRepository<Performance, Long> {

    List<Performance> findAllByShowIdOrderByStartTimeAscPerformanceNoAsc(Long showId);
}
