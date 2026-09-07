package com.ticket.show.infrastructure.show;

import com.ticket.show.domain.show.Genre;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface SpringDataGenreJpaRepository extends JpaRepository<Genre, Long> {

    List<Genre> findAllByOrderByCategory_IdAscNameAsc();

    List<Genre> findAllByCategory_CodeOrderByName(String categoryCode);
}
