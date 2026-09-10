package com.ticket.booking.hold.infrastructure;

import com.ticket.booking.hold.domain.HoldHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface SpringDataHoldHistoryJpaRepository extends JpaRepository<HoldHistory, Long> {

    List<HoldHistory> findAllByHoldKeyOrderByIdAsc(String holdKey);
}
