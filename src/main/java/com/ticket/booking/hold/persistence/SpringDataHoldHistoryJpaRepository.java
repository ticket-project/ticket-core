package com.ticket.booking.hold.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ticket.booking.hold.domain.HoldHistory;

interface SpringDataHoldHistoryJpaRepository extends JpaRepository<HoldHistory, Long> {}
