package com.ticket.show.performance.infrastructure;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ticket.show.performance.domain.Performance;

interface SpringDataPerformanceJpaRepository extends JpaRepository<Performance, Long> {
    List<Performance> findAllByShowIdOrderByStartTimeAscPerformanceNoAsc(Long showId);
}
