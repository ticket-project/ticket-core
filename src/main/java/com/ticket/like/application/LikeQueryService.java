package com.ticket.like.application;

import com.ticket.like.application.port.LikeQueryPort;

import com.ticket.like.LikeEntry;
import com.ticket.like.LikeInfo;
import com.ticket.like.LikeQuery;
import com.ticket.like.LikeType;
import com.ticket.like.domain.LikeRepository;
import com.ticket.shared.CursorPage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * {@link LikeQuery}의 like 소유 구현이다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LikeQueryService implements LikeQuery {

    private final LikeRepository likeRepository;
    private final LikeQueryPort likeQueryPort;

    @Override
    public LikeInfo get(final LikeType likeType, final long targetId, final long memberId) {
        final boolean liked = likeRepository.existsByMemberIdAndLikeTypeAndTargetId(memberId, likeType, targetId);
        final long likeCount = likeRepository.countByLikeTypeAndTargetId(likeType, targetId);
        return new LikeInfo(liked, likeCount);
    }

    @Override
    public long countByTarget(final LikeType likeType, final long targetId) {
        return likeRepository.countByLikeTypeAndTargetId(likeType, targetId);
    }

    @Override
    public CursorPage<LikeEntry, Long> findLiked(
            final LikeType likeType, final long memberId, final Long cursorLikeId, final int size) {
        final CursorPage<LikeRow, Long> page = likeQueryPort.findLiked(likeType, memberId, cursorLikeId, size);
        final List<LikeEntry> entries = page.items().stream()
                .map(row -> new LikeEntry(row.likeId(), row.targetId(), row.likedAt()))
                .toList();
        return new CursorPage<>(entries, page.hasNext(), page.nextPosition());
    }
}
