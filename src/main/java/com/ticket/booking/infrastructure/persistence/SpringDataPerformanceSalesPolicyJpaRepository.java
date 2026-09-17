package com.ticket.booking.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ticket.booking.domain.salespolicy.PerformanceSalesPolicy;

interface SpringDataPerformanceSalesPolicyJpaRepository
        extends JpaRepository<PerformanceSalesPolicy, Long> {}
