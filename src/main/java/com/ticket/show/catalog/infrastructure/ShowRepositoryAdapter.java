package com.ticket.show.catalog.infrastructure;

import com.ticket.show.catalog.domain.Show;
import com.ticket.show.catalog.domain.ShowRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * {@link ShowRepository}의 JPA 구현이다.
 */
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
