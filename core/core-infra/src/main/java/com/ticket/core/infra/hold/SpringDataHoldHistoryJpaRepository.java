package com.ticket.core.infra.hold;

import com.ticket.core.domain.hold.model.HoldHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface SpringDataHoldHistoryJpaRepository extends JpaRepository<HoldHistory, Long> {

    List<HoldHistory> findAllByHoldKeyOrderByIdAsc(String holdKey);
}
