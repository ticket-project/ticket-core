package com.ticket.core.domain.performance.repository;

import com.ticket.core.domain.performance.model.Performance;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PerformanceRepository extends JpaRepository<Performance, Long> {
    List<Performance> findAllByShowIdOrderByStartTimeAscPerformanceNoAsc(Long showId);

    @Query("select p from Performance p left join fetch p.queuePolicy where p.id = :id")
    Optional<Performance> findWithQueuePolicyById(@Param("id") Long id);
}
