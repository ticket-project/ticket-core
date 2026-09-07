package com.ticket.booking.infrastructure.performancepolicy;

import com.ticket.booking.domain.performancepolicy.model.PerformanceSalesPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataPerformanceSalesPolicyJpaRepository extends JpaRepository<PerformanceSalesPolicy, Long> {
}
