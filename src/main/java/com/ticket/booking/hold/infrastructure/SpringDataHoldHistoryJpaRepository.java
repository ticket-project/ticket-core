package com.ticket.booking.hold.infrastructure;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ticket.booking.hold.domain.HoldHistory;

interface SpringDataHoldHistoryJpaRepository extends JpaRepository<HoldHistory, Long> {
    List<HoldHistory> findAllByHoldKeyOrderByIdAsc(String holdKey);
}
