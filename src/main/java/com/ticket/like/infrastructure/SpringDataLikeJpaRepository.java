package com.ticket.like.infrastructure;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ticket.like.LikeType;
import com.ticket.like.domain.Like;

interface SpringDataLikeJpaRepository extends JpaRepository<Like, Long> {
    boolean existsByMemberIdAndLikeTypeAndTargetId(Long memberId, LikeType likeType, Long targetId);

    Optional<Like> findByMemberIdAndLikeTypeAndTargetId(
            Long memberId, LikeType likeType, Long targetId);

    long countByLikeTypeAndTargetId(LikeType likeType, Long targetId);
}
