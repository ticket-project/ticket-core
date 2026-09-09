package com.ticket.show.infrastructure;

import com.ticket.show.domain.Performance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface SpringDataPerformanceJpaRepository extends JpaRepository<Performance, Long> {

    List<Performance> findAllByShowIdOrderByStartTimeAscPerformanceNoAsc(Long showId);
}
