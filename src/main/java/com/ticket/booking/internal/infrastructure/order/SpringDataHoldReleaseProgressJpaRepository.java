package com.ticket.booking.internal.infrastructure.order;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface SpringDataHoldReleaseProgressJpaRepository extends JpaRepository<HoldReleaseProgress, UUID> {
}
