package com.ticket.favorite;

import com.ticket.shared.CursorPage;

/**
 * 찜 읽기 전용 조회 공개 계약이다. show entity를 노출하지 않는다.
 */
public interface ShowLikeQuery {

    /**
     * 특정 회원의 특정 공연 찜 여부와 그 공연의 전체 찜 개수를 함께 반환한다.
     */
    ShowLikeInfo getShowLike(long showId, long memberId);

    /**
     * 공연 하나의 전체 찜 개수만 반환한다. 찜이 하나도 없으면 0이다. show 존재 여부는 확인하지
     * 않는다 — 존재하지 않는 showId도 0을 돌려준다.
     */
    long countByShowId(long showId);

    /**
     * 특정 회원이 찜한 공연 목록을 최신순(likeId 내림차순)으로 한 페이지 조회한다.
     * {@code cursorLikeId}는 이전 페이지 마지막 항목의 likeId다(첫 페이지는 null).
     */
    CursorPage<ShowLikeEntry, Long> findLikedShows(long memberId, Long cursorLikeId, int size);
}
