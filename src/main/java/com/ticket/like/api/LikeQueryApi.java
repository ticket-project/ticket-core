package com.ticket.like.api;

import org.jspecify.annotations.Nullable;

import com.ticket.shared.api.CursorPage;

/**
 * 찜 읽기 전용 조회 공개 계약이다. 대상 entity를 노출하지 않는다.
 *
 * <p>찜 대상의 종류는 이 계약에 나오지 않는다 — 대상마다 메서드를 나눈다. 종류를 값으로 받으면 그 값 집합({@code like.domain}의 {@code LikeType})을 호출하는 module이
 * 함께 알아야 하고, 공연장 module이 "공연 찜"을 넘기는 것도 막지 못한다. 새 찜 대상이 생기면 그 대상을 아는 module이 쓸 메서드를 여기 더한다.
 */
public interface LikeQueryApi {
    /** 공연 하나의 전체 찜 개수다. 찜이 하나도 없으면 0이다. 공연 존재 여부는 확인하지 않는다 — 존재하지 않는 showId도 0을 돌려준다. */
    long countShowLikes(long showId);

    /** 특정 회원이 찜한 공연 목록을 최신순(likeId 내림차순)으로 한 페이지 조회한다. {@code cursorLikeId}는 이전 페이지 마지막 항목의 likeId다(첫 페이지는 null). */
    CursorPage<LikeSnapshot, Long> findLikedShows(long memberId, @Nullable Long cursorLikeId, int size);
}
