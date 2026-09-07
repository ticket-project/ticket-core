package com.ticket.show.infrastructure.show;

import com.ticket.show.domain.show.Genre;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

interface SpringDataGenreJpaRepository extends JpaRepository<Genre, Long> {

    List<Genre> findAllByOrderByCategoryIdAscNameAsc();

    /**
     * Category는 Genre와 다른 aggregate라 {@code categoryId} scalar로만 연결된다 — 옛
     * {@code findAllByCategory_CodeOrderByName} 파생 쿼리(연관관계 경로 탐색)를 명시적 join JPQL로
     * 바꿨다.
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
