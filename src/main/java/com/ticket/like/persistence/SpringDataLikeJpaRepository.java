package com.ticket.like.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ticket.like.domain.Like;

interface SpringDataLikeJpaRepository extends JpaRepository<Like, Long> {
    boolean existsByMemberIdAndTargetId(Long memberId, Long targetId);

    Optional<Like> findByMemberIdAndTargetId(Long memberId, Long targetId);

    long countByTargetId(Long targetId);

    @Query("SELECT l FROM Like l WHERE l.memberId = :memberId ORDER BY l.id DESC")
    List<Like> findFirstPage(@Param("memberId") Long memberId, Pageable pageable);

    @Query("""
            SELECT l FROM Like l
            WHERE l.memberId = :memberId AND l.id < :cursorLikeId
            ORDER BY l.id DESC
            """)
    List<Like> findAfterId(
            @Param("memberId") Long memberId, @Param("cursorLikeId") Long cursorLikeId, Pageable pageable);
}
