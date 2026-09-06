package com.ticket.booking.domain.hold.repository;

import com.ticket.booking.domain.hold.model.HoldHistory;

import java.util.List;

/**
 * hold 이력의 저장과 복원을 담당하는 도메인 Repository다.
 */
public interface HoldHistoryRepository {

    List<HoldHistory> saveAll(List<HoldHistory> holdHistories);

    List<HoldHistory> findAllByHoldKeyOrderByIdAsc(String holdKey);
}
