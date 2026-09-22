package com.ticket.like.usecase;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ticket.like.api.LikeQueryApi;
import com.ticket.like.api.LikeSnapshot;
import com.ticket.like.domain.Like;
import com.ticket.like.domain.LikeRepository;
import com.ticket.like.domain.LikeType;
import com.ticket.shared.api.CursorPage;

import lombok.RequiredArgsConstructor;

/** {@link LikeQueryApi}의 like 소유 구현이다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LikeQueryService implements LikeQueryApi {
    private final LikeRepository likeRepository;

    /** like 안에서만 쓰는 조회다 — 공개 계약이 아니다. 다른 module은 찜 여부를 묻지 않는다. */
    public LikeCountSnapshot countByTargetForMember(final LikeType likeType, final long targetId, final long memberId) {
        final boolean liked = likeRepository.existsByMemberIdAndLikeTypeAndTargetId(memberId, likeType, targetId);
        final long likeCount = likeRepository.countByLikeTypeAndTargetId(likeType, targetId);
        return new LikeCountSnapshot(liked, likeCount);
    }

    @Override
    public long countByTarget(final String targetType, final long targetId) {
        return likeRepository.countByLikeTypeAndTargetId(LikeType.from(targetType), targetId);
    }

    @Override
    public CursorPage<LikeSnapshot, Long> findLiked(
            final String targetType, final long memberId, final @Nullable Long cursorLikeId, final int size) {
        // size가 0이면 adapter의 subList(0, 0)·getLast()가 NoSuchElementException으로 터진다. 계약
        // 위반이 구현 세부 예외로 새어 나가지 않도록 공개 계약을 구현하는 여기서 끊는다.
        if (size < 1) {
            throw new IllegalArgumentException("size는 1 이상이어야 합니다: " + size);
        }
        return likeRepository
                .findLiked(LikeType.from(targetType), memberId, cursorLikeId, size)
                .map(LikeQueryService::toEntry);
    }

    private static LikeSnapshot toEntry(final Like like) {
        return new LikeSnapshot(like.getId(), like.getTargetId(), like.getCreatedAt());
    }
}
