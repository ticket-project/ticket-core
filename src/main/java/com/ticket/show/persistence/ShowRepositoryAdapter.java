package com.ticket.show.persistence;

import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.ticket.show.domain.show.Show;
import com.ticket.show.domain.show.ShowRepository;

import lombok.RequiredArgsConstructor;

/** {@link ShowRepository}의 JPA 구현이다. */
@Repository
@RequiredArgsConstructor
public class ShowRepositoryAdapter implements ShowRepository {
    private final SpringDataShowJpaRepository jpaRepository;

    @Override
    public Optional<Show> findById(final Long showId) {
        return jpaRepository.findById(showId);
    }

    @Override
    public boolean existsById(final Long showId) {
        return jpaRepository.existsById(showId);
    }
}
