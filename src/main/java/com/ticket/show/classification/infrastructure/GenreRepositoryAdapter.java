package com.ticket.show.classification.infrastructure;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.ticket.show.classification.domain.Genre;
import com.ticket.show.classification.domain.GenreRepository;

import lombok.RequiredArgsConstructor;

/** {@link GenreRepository}의 JPA 구현이다. */
@Repository
@RequiredArgsConstructor
public class GenreRepositoryAdapter implements GenreRepository {
    private final SpringDataGenreJpaRepository jpaRepository;

    @Override
    public List<Genre> findAllOrderByCategoryAndName() {
        return jpaRepository.findAllByOrderByCategoryIdAscNameAsc();
    }

    @Override
    public List<Genre> findAllByCategoryCodeOrderByName(final String categoryCode) {
        return jpaRepository.findAllByCategoryCodeOrderByName(categoryCode);
    }
}
