package com.ticket.catalog.internal.infrastructure.show;

import com.ticket.catalog.internal.domain.show.Genre;
import com.ticket.catalog.internal.domain.show.repository.GenreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * {@link GenreRepository}의 JPA 구현이다.
 */
@Repository
@RequiredArgsConstructor
public class GenreRepositoryAdapter implements GenreRepository {

    private final SpringDataGenreJpaRepository jpaRepository;

    @Override
    public List<Genre> findAllOrderByCategoryAndName() {
        return jpaRepository.findAllByOrderByCategory_IdAscNameAsc();
    }

    @Override
    public List<Genre> findAllByCategoryCodeOrderByName(final String categoryCode) {
        return jpaRepository.findAllByCategory_CodeOrderByName(categoryCode);
    }
}
