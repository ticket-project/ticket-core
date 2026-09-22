package com.ticket.show.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ticket.show.domain.show.Show;

interface SpringDataShowJpaRepository extends JpaRepository<Show, Long> {
    /**
     * Genre는 Show와 다른 aggregate라 {@code ShowGenre}의 {@code genreId} scalar로만 연결된다 — 연관관계 경로 탐색 대신 명시적 join JPQL을
     * 쓴다({@code SpringDataGenreJpaRepository}와 같은 방식).
     */
    @Query("""
            SELECT g.name
            FROM ShowGenre sg
            JOIN Genre g ON g.id = sg.genreId
            WHERE sg.showId = :showId
            """)
    List<String> findGenreNamesByShowId(@Param("showId") Long showId);
}
