package com.ticket.show.performance.infrastructure;

import com.ticket.show.performance.domain.Performance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface SpringDataPerformanceJpaRepository extends JpaRepository<Performance, Long> {

    List<Performance> findAllByShowIdOrderByStartTimeAscPerformanceNoAsc(Long showId);
}
