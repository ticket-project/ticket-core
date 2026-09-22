package com.ticket.show.persistence;

import java.util.List;

import org.jspecify.annotations.Nullable;
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

    @Query("""
            SELECT s.id AS showId, g.name AS genreName
            FROM Show s
            LEFT JOIN ShowGenre sg ON sg.showId = s.id
            LEFT JOIN Genre g ON g.id = sg.genreId
            WHERE s.id IN :showIds
            """)
    List<ShowGenreName> findGenreNamesByShowIds(@Param("showIds") List<Long> showIds);

    interface ShowGenreName {
        Long getShowId();

        @Nullable
        String getGenreName();
    }
}
