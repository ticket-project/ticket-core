package com.ticket.show.persistence;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.ticket.show.domain.Genre;
import com.ticket.show.domain.GenreRepository;

import lombok.RequiredArgsConstructor;

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
