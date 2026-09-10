package com.ticket.like.preference.infrastructure;

import com.ticket.like.LikeType;
import com.ticket.like.preference.domain.Like;
import com.ticket.like.preference.domain.LikeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * {@link LikeRepository}의 JPA 구현이다.
 *
 * <p>member·대상 모두 scalar id다(모듈을 넘나드는 JPA 연관관계 금지, ADR 0003 §4) — 그래서
 * {@code EntityManager.getReference}로 FK 전용 참조를 미리 만들 필요 없이 값 그대로 entity를
 * 구성한다.
 */
@Repository
@RequiredArgsConstructor
public class LikeRepositoryAdapter implements LikeRepository {

    private final SpringDataLikeJpaRepository jpaRepository;

    @Override
    public Like like(final Long memberId, final LikeType likeType, final Long targetId) {
        return jpaRepository.save(new Like(memberId, likeType, targetId));
    }

    @Override
    public void delete(final Like like) {
        jpaRepository.delete(like);
    }

    @Override
    public boolean existsByMemberIdAndLikeTypeAndTargetId(
            final Long memberId, final LikeType likeType, final Long targetId) {
        return jpaRepository.existsByMemberIdAndLikeTypeAndTargetId(memberId, likeType, targetId);
    }

    @Override
    public Optional<Like> findByMemberIdAndLikeTypeAndTargetId(
            final Long memberId, final LikeType likeType, final Long targetId) {
        return jpaRepository.findByMemberIdAndLikeTypeAndTargetId(memberId, likeType, targetId);
    }

    @Override
    public long countByLikeTypeAndTargetId(final LikeType likeType, final Long targetId) {
        return jpaRepository.countByLikeTypeAndTargetId(likeType, targetId);
    }
}
