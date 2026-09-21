package com.ticket.like.usecase;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ticket.like.api.LikeEntry;
import com.ticket.like.api.LikeInfo;
import com.ticket.like.api.LikeQueryApi;
import com.ticket.like.api.LikeType;
import com.ticket.like.domain.LikeRepository;
import com.ticket.like.persistence.LikeQuerydslRepository;
import com.ticket.shared.api.CursorPage;

import lombok.RequiredArgsConstructor;

/** {@link LikeQueryApi}의 like 소유 구현이다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LikeQueryService implements LikeQueryApi {
    private final LikeRepository likeRepository;
    private final LikeQuerydslRepository likeQuerydslRepository;

    @Override
    public LikeInfo get(final LikeType likeType, final long targetId, final long memberId) {
        final boolean liked =
                likeRepository.existsByMemberIdAndLikeTypeAndTargetId(memberId, likeType, targetId);
        final long likeCount = likeRepository.countByLikeTypeAndTargetId(likeType, targetId);
        return new LikeInfo(liked, likeCount);
    }

    @Override
    public long countByTarget(final LikeType likeType, final long targetId) {
        return likeRepository.countByLikeTypeAndTargetId(likeType, targetId);
    }

    @Override
    public CursorPage<LikeEntry, Long> findLiked(
            final LikeType likeType,
            final long memberId,
            final @Nullable Long cursorLikeId,
            final int size) {
        return likeQuerydslRepository.findLiked(likeType, memberId, cursorLikeId, size);
    }
}
