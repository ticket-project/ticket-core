package com.ticket.like.preference.infrastructure;

import com.ticket.like.LikeType;
import com.ticket.like.preference.domain.Like;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

interface SpringDataLikeJpaRepository extends JpaRepository<Like, Long> {

    boolean existsByMemberIdAndLikeTypeAndTargetId(Long memberId, LikeType likeType, Long targetId);

    Optional<Like> findByMemberIdAndLikeTypeAndTargetId(Long memberId, LikeType likeType, Long targetId);

    long countByLikeTypeAndTargetId(LikeType likeType, Long targetId);
}
