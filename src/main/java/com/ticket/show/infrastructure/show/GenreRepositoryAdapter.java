package com.ticket.show.infrastructure.show;

import com.ticket.show.domain.show.Genre;
import com.ticket.show.domain.show.repository.GenreRepository;
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
        return jpaRepository.findAllByOrderByCategoryIdAscNameAsc();
    }

    @Override
    public List<Genre> findAllByCategoryCodeOrderByName(final String categoryCode) {
        return jpaRepository.findAllByCategoryCodeOrderByName(categoryCode);
    }
}
