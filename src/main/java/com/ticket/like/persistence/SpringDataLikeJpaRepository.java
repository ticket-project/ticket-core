package com.ticket.like.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ticket.like.api.LikeType;
import com.ticket.like.domain.Like;

interface SpringDataLikeJpaRepository extends JpaRepository<Like, Long> {
    boolean existsByMemberIdAndLikeTypeAndTargetId(Long memberId, LikeType likeType, Long targetId);

    Optional<Like> findByMemberIdAndLikeTypeAndTargetId(Long memberId, LikeType likeType, Long targetId);

    long countByLikeTypeAndTargetId(LikeType likeType, Long targetId);

    @Query("SELECT l FROM Like l WHERE l.memberId = :memberId AND l.likeType = :likeType ORDER BY l.id DESC")
    List<Like> findFirstPage(@Param("likeType") LikeType likeType, @Param("memberId") Long memberId, Pageable pageable);

    @Query("""
            SELECT l FROM Like l
            WHERE l.memberId = :memberId AND l.likeType = :likeType AND l.id < :cursorLikeId
            ORDER BY l.id DESC
            """)
    List<Like> findAfterId(
            @Param("likeType") LikeType likeType,
            @Param("memberId") Long memberId,
            @Param("cursorLikeId") Long cursorLikeId,
            Pageable pageable);
}
