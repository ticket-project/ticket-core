package com.ticket.booking.infrastructure.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ticket.booking.domain.hold.HoldHistory;

interface SpringDataHoldHistoryJpaRepository extends JpaRepository<HoldHistory, Long> {
    List<HoldHistory> findAllByHoldKeyOrderByIdAsc(String holdKey);
}
