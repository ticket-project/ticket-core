package com.ticket.like.domain;

import com.ticket.like.LikeType;

import java.util.Optional;

/**
 * 찜 aggregate의 저장과 복원을 담당하는 도메인 Repository다.
 *
 * <p>{@link #like(Long, LikeType, Long)}은 scalar id만 받는다 — memberId/targetId 둘 다 이
 * module 밖의 값이라 entity를 실제로 구성하는 일은 이 인터페이스의 구현({@code LikeRepositoryAdapter})에
 * 맡긴다.
 */
public interface LikeRepository {

    Like like(Long memberId, LikeType likeType, Long targetId);

    void delete(Like like);

    boolean existsByMemberIdAndLikeTypeAndTargetId(Long memberId, LikeType likeType, Long targetId);

    Optional<Like> findByMemberIdAndLikeTypeAndTargetId(Long memberId, LikeType likeType, Long targetId);

    long countByLikeTypeAndTargetId(LikeType likeType, Long targetId);
}
