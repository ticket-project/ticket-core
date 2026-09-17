package com.ticket.booking.infrastructure.persistence;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.ticket.booking.domain.hold.HoldHistory;
import com.ticket.booking.domain.hold.HoldHistoryRepository;

import lombok.RequiredArgsConstructor;

/** {@link HoldHistoryRepository}의 JPA 구현이다. */
@Repository
@RequiredArgsConstructor
public class HoldHistoryRepositoryAdapter implements HoldHistoryRepository {
    private final SpringDataHoldHistoryJpaRepository jpaRepository;

    @Override
    public List<HoldHistory> saveAll(final List<HoldHistory> holdHistories) {
        return jpaRepository.saveAll(holdHistories);
    }

    @Override
    public List<HoldHistory> findAllByHoldKeyOrderByIdAsc(final String holdKey) {
        return jpaRepository.findAllByHoldKeyOrderByIdAsc(holdKey);
    }
}
