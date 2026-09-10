package com.ticket.booking.salespolicy.infrastructure;

import com.ticket.booking.salespolicy.domain.PerformanceSalesPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataPerformanceSalesPolicyJpaRepository extends JpaRepository<PerformanceSalesPolicy, Long> {
}
