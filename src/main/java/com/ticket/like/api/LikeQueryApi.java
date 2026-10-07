package com.ticket.like.api;

import org.jspecify.annotations.Nullable;

import com.ticket.shared.api.CursorPage;

/**
 * 찜 읽기 전용 조회 공개 계약이다. 대상 entity를 노출하지 않는다.
 *
 * <p>찜 대상은 지금 공연(Show)뿐이라 대상 종류를 받지 않는다. {@code targetId}는 공연 ID다. 새 대상이 생기면 그때 종류 인자를 다시 둔다(ADR 0008).
 */
public interface LikeQueryApi {
    /** 대상 하나의 전체 찜 개수다. 찜이 하나도 없으면 0이다. 대상 존재 여부는 확인하지 않는다. */
    long countByTarget(long targetId);

    /**
     * 특정 회원이 찜한 대상을 최신순(likeId 내림차순)으로 한 페이지 조회한다. {@code cursorLikeId}는 이전 페이지 마지막 항목의 likeId다(첫 페이지는 null) — 그보다 작은
     * likeId만 이어서 읽는다.
     *
     * @param size 한 페이지에 담을 최대 개수. <b>1 이상</b>이어야 한다. 상한은 이 계약이 정하지 않는다 — 호출하는 module이 자기 API 계약에 맞춰 정한다.
     * @throws IllegalArgumentException {@code size}가 1 미만일 때
     */
    CursorPage<LikeSnapshot, Long> findLiked(long memberId, @Nullable Long cursorLikeId, int size);
}
