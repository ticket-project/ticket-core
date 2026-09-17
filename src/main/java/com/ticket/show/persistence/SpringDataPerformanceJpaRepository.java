package com.ticket.show.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ticket.show.domain.performance.Performance;

interface SpringDataPerformanceJpaRepository extends JpaRepository<Performance, Long> {
    List<Performance> findAllByShowIdOrderByStartTimeAscPerformanceNoAsc(Long showId);
}
