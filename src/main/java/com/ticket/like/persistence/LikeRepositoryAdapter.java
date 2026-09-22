package com.ticket.like.persistence;

import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import com.ticket.like.api.LikeType;
import com.ticket.like.domain.Like;
import com.ticket.like.domain.LikeRepository;
import com.ticket.shared.api.CursorPage;

import lombok.RequiredArgsConstructor;

/**
 * {@link LikeRepository}의 JPA 구현이다.
 *
 * <p>member·대상 모두 scalar id다(모듈을 넘나드는 JPA 연관관계 금지, ADR 0003 §4) — 그래서 {@code EntityManager.getReference}로 FK 전용 참조를 미리
 * 만들 필요 없이 값 그대로 entity를 구성한다.
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

    @Override
    public CursorPage<Like, Long> findLiked(
            final LikeType likeType, final Long memberId, final @Nullable Long cursorLikeId, final int size) {
        final PageRequest page = PageRequest.of(0, size + 1);
        final List<Like> rows = cursorLikeId == null
                ? jpaRepository.findFirstPage(likeType, memberId, page)
                : jpaRepository.findAfterId(likeType, memberId, cursorLikeId, page);
        if (rows.isEmpty()) {
            return CursorPage.empty();
        }
        final boolean hasNext = rows.size() > size;
        final List<Like> pageRows = hasNext ? rows.subList(0, size) : rows;
        final @Nullable Long nextPosition = hasNext ? pageRows.getLast().getId() : null;
        return new CursorPage<>(pageRows, hasNext, nextPosition);
    }
}
