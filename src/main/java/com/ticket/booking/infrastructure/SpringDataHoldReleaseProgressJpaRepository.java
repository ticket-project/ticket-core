package com.ticket.booking.infrastructure;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataHoldReleaseProgressJpaRepository
        extends JpaRepository<HoldReleaseProgress, UUID> {}
