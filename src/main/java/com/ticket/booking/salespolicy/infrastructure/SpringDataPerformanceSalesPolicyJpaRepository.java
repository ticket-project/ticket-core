package com.ticket.booking.salespolicy.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ticket.booking.salespolicy.domain.PerformanceSalesPolicy;

interface SpringDataPerformanceSalesPolicyJpaRepository
        extends JpaRepository<PerformanceSalesPolicy, Long> {}
