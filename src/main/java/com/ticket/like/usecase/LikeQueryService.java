package com.ticket.like.usecase;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ticket.like.api.LikeCountSnapshot;
import com.ticket.like.api.LikeQueryApi;
import com.ticket.like.api.LikeSnapshot;
import com.ticket.like.api.LikeType;
import com.ticket.like.domain.Like;
import com.ticket.like.domain.LikeRepository;
import com.ticket.shared.api.CursorPage;

import lombok.RequiredArgsConstructor;

/** {@link LikeQueryApi}의 like 소유 구현이다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LikeQueryService implements LikeQueryApi {
    private final LikeRepository likeRepository;

    @Override
    public LikeCountSnapshot countByTargetForMember(final LikeType likeType, final long targetId, final long memberId) {
        final boolean liked = likeRepository.existsByMemberIdAndLikeTypeAndTargetId(memberId, likeType, targetId);
        final long likeCount = likeRepository.countByLikeTypeAndTargetId(likeType, targetId);
        return new LikeCountSnapshot(liked, likeCount);
    }

    @Override
    public long countByTarget(final LikeType likeType, final long targetId) {
        return likeRepository.countByLikeTypeAndTargetId(likeType, targetId);
    }

    @Override
    public CursorPage<LikeSnapshot, Long> findLiked(
            final LikeType likeType, final long memberId, final @Nullable Long cursorLikeId, final int size) {
        return likeRepository.findLiked(likeType, memberId, cursorLikeId, size).map(LikeQueryService::toEntry);
    }

    private static LikeSnapshot toEntry(final Like like) {
        return new LikeSnapshot(like.getId(), like.getTargetId(), like.getCreatedAt());
    }
}
