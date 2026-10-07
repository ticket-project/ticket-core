package com.ticket.like.usecase;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ticket.like.api.LikeQueryApi;
import com.ticket.like.api.LikeSnapshot;
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
    public long countByTarget(final long targetId) {
        return likeRepository.countByTargetId(targetId);
    }

    @Override
    public CursorPage<LikeSnapshot, Long> findLiked(
            final long memberId, final @Nullable Long cursorLikeId, final int size) {
        // size가 0이면 adapter의 subList(0, 0)·getLast()가 NoSuchElementException으로 터진다. 계약
        // 위반이 구현 세부 예외로 새어 나가지 않도록 공개 계약을 구현하는 여기서 끊는다.
        if (size < 1) {
            throw new IllegalArgumentException("size는 1 이상이어야 합니다: " + size);
        }
        return likeRepository.findLiked(memberId, cursorLikeId, size).map(LikeQueryService::toEntry);
    }

    private static LikeSnapshot toEntry(final Like like) {
        return new LikeSnapshot(like.getId(), like.getTargetId(), like.getCreatedAt());
    }
}
