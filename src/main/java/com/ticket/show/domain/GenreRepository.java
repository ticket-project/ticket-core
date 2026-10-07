package com.ticket.show.domain;

import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** 장르 메타 코드의 복원을 담당하는 도메인 Repository다. 구현은 Spring Data JPA가 만든다. */
public interface GenreRepository extends Repository<Genre, Long> {
    default List<Genre> findAllOrderByCategoryAndName() {
        return findAllByOrderByCategoryIdAscNameAsc();
    }

    List<Genre> findAllByOrderByCategoryIdAscNameAsc();

    /**
     * Category는 Genre와 다른 aggregate라 {@code categoryId} scalar로만 연결된다 — 옛 {@code findAllByCategory_CodeOrderByName} 파생
     * 쿼리(연관관계 경로 탐색)를 명시적 join JPQL로 바꿨다.
     */
    @Query("""
            SELECT g
            FROM Genre g
            JOIN Category c ON c.id = g.categoryId
            WHERE c.code = :categoryCode
            ORDER BY g.name
            """)
    List<Genre> findAllByCategoryCodeOrderByName(@Param("categoryCode") String categoryCode);
}
