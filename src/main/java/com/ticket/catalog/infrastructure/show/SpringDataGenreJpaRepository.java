package com.ticket.catalog.infrastructure.show;

import com.ticket.catalog.domain.show.Genre;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface SpringDataGenreJpaRepository extends JpaRepository<Genre, Long> {

    List<Genre> findAllByOrderByCategory_IdAscNameAsc();

    List<Genre> findAllByCategory_CodeOrderByName(String categoryCode);
}
