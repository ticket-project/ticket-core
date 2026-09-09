package com.ticket.booking.infrastructure;

import com.ticket.booking.domain.PerformanceSalesPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataPerformanceSalesPolicyJpaRepository extends JpaRepository<PerformanceSalesPolicy, Long> {
}
