package com.ticket.booking.event.persistence;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataHoldReleaseProgressJpaRepository extends JpaRepository<HoldReleaseProgress, UUID> {}
