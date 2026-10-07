package com.ticket.show.domain.show;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * 공연 aggregate의 복원을 담당하는 도메인 Repository다. 구현은 Spring Data JPA가 만든다.
 *
 * <p>동적 조건·커서 페이징이 필요한 목록/검색 조회는 {@code show.persistence}의 Querydsl 조회가 담당한다.
 *
 * <p>조회 결과가 없다는 사실만 알려 주고, 그것을 어떤 오류로 볼지는 호출하는 유스케이스가 정한다. 맥락에 따라 인증 실패일 수도, not-found일 수도, 멱등 성공일 수도 있다.
 */
public interface ShowRepository extends Repository<Show, Long> {
    Optional<Show> findById(Long showId);

    /** 찜 목록처럼 id 집합으로 show를 한 번에 복원한다. 빈 {@code showIds}는 빈 map을 반환한다. */
    default Map<Long, Show> findSummaries(final Set<Long> showIds) {
        if (showIds.isEmpty()) {
            return Map.of();
        }
        return findAllById(showIds).stream().collect(Collectors.toMap(Show::getId, show -> show));
    }

    List<Show> findAllById(Iterable<Long> showIds);

    /** 장르 이름을 한 번에 조회한다. 장르가 없는 공연은 결과에 포함하지 않는다. */
    default Map<Long, List<String>> findGenreNamesByShowIds(final List<Long> showIds) {
        if (showIds.isEmpty()) {
            return Map.of();
        }
        final Map<Long, List<String>> genreNames = new LinkedHashMap<>();
        for (final ShowGenreName row : findGenreNameRows(showIds)) {
            final String name = row.getGenreName();
            if (name != null) {
                genreNames
                        .computeIfAbsent(row.getShowId(), key -> new ArrayList<>())
                        .add(name);
            }
        }
        return genreNames;
    }

    /**
     * Genre는 Show와 다른 aggregate라 {@code ShowGenre}의 {@code genreId} scalar로만 연결된다 — 연관관계 경로 탐색 대신 명시적 join JPQL을
     * 쓴다({@code GenreRepository}와 같은 방식).
     */
    @Query("""
            SELECT s.id AS showId, g.name AS genreName
            FROM Show s
            LEFT JOIN ShowGenre sg ON sg.showId = s.id
            LEFT JOIN Genre g ON g.id = sg.genreId
            WHERE s.id IN :showIds
            """)
    List<ShowGenreName> findGenreNameRows(@Param("showIds") List<Long> showIds);

    interface ShowGenreName {
        Long getShowId();

        @Nullable
        String getGenreName();
    }
}
