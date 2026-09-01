package com.ticket.core.infra.show;

import com.ticket.core.domain.show.model.Genre;
import com.ticket.core.domain.show.repository.GenreRepository;
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
