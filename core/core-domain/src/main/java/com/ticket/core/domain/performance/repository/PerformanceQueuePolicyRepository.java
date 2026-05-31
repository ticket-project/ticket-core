package com.ticket.core.domain.performance.repository;

import com.ticket.core.domain.performance.model.PerformanceQueuePolicy;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PerformanceQueuePolicyRepository extends JpaRepository<PerformanceQueuePolicy, Long> {
}
