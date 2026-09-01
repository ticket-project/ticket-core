package com.ticket.core.infra.hold;

import com.ticket.core.domain.hold.model.HoldHistory;
import com.ticket.core.domain.hold.repository.HoldHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * {@link HoldHistoryRepository}의 JPA 구현이다.
 */
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
