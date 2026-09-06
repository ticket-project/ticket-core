package com.ticket.catalog.infrastructure.performance;

import com.ticket.catalog.domain.performance.Performance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

interface SpringDataPerformanceJpaRepository extends JpaRepository<Performance, Long> {

    List<Performance> findAllByShowIdOrderByStartTimeAscPerformanceNoAsc(Long showId);

    @Query("select p from Performance p left join fetch p.queuePolicy where p.id = :id")
    Optional<Performance> findWithQueuePolicyById(@Param("id") Long id);
}
