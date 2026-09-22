package com.ticket.like.api;

import org.jspecify.annotations.Nullable;

import com.ticket.shared.api.CursorPage;

/**
 * 찜 읽기 전용 조회 공개 계약이다. 대상 entity를 노출하지 않는다.
 *
 * <p>대상 종류는 문자열로 받는다. 저장 모델의 {@code LikeType}은 like domain에 남기고, 호출하는 module은 자신이 조회할 대상 종류를 명시한다.
 */
public interface LikeQueryApi {
    /** 대상 하나의 전체 찜 개수다. 찜이 하나도 없으면 0이다. 대상 존재 여부는 확인하지 않는다. */
    long countByTarget(String targetType, long targetId);

    /** 특정 회원이 찜한 대상을 최신순(likeId 내림차순)으로 한 페이지 조회한다. {@code cursorLikeId}는 이전 페이지 마지막 항목의 likeId다(첫 페이지는 null). */
    CursorPage<LikeSnapshot, Long> findLiked(String targetType, long memberId, @Nullable Long cursorLikeId, int size);
}
