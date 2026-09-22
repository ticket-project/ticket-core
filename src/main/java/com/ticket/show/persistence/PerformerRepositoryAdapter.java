package com.ticket.show.persistence;

import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.ticket.show.domain.Performer;
import com.ticket.show.domain.PerformerRepository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class PerformerRepositoryAdapter implements PerformerRepository {
    private final SpringDataPerformerJpaRepository jpaRepository;

    @Override
    public Optional<Performer> findById(final Long performerId) {
        return jpaRepository.findById(performerId);
    }
}
