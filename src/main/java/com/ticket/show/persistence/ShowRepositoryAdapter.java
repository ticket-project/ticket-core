package com.ticket.show.persistence;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

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

    @Override
    public Map<Long, Show> findSummaries(final Set<Long> showIds) {
        if (showIds.isEmpty()) {
            return Map.of();
        }
        return jpaRepository.findAllById(showIds).stream()
                .collect(Collectors.toMap(Show::getId, showEntity -> showEntity));
    }

    @Override
    public List<String> findGenreNames(final Long showId) {
        return jpaRepository.findGenreNamesByShowId(showId);
    }
}
