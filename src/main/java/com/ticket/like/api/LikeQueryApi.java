package com.ticket.like.api;

import org.jspecify.annotations.Nullable;

import com.ticket.shared.api.CursorPage;

/**
 * 찜 읽기 전용 조회 공개 계약이다. 대상 entity를 노출하지 않는다.
 *
 * <p>대상 종류는 문자열로 받는다. 저장 모델의 {@code LikeType}은 like domain에 남기고, 호출하는 module은 자신이 조회할 대상 종류를 명시한다.
 *
 * <p><b>{@code targetType} 계약.</b> 지금 허용되는 값은 {@code "show"} 하나뿐이다. 소문자 정확 일치이며 앞뒤 공백을 다듬지 않는다 — 상수처럼 쓰라는 뜻이다. 그 밖의 값은
 * {@link IllegalArgumentException}이다. 이것은 사용자 입력 오류가 아니라 호출하는 module의 프로그래밍 오류이므로 4xx 응답으로 바꾸지 않는다(사용자 입력을 그대로 넘기지 말라는
 * 뜻이기도 하다).
 */
public interface LikeQueryApi {
    /** 대상 하나의 전체 찜 개수다. 찜이 하나도 없으면 0이다. 대상 존재 여부는 확인하지 않는다. */
    long countByTarget(String targetType, long targetId);

    /**
     * 특정 회원이 찜한 대상을 최신순(likeId 내림차순)으로 한 페이지 조회한다. {@code cursorLikeId}는 이전 페이지 마지막 항목의 likeId다(첫 페이지는 null) — 그보다 작은
     * likeId만 이어서 읽는다.
     *
     * @param size 한 페이지에 담을 최대 개수. <b>1 이상</b>이어야 한다. 상한은 이 계약이 정하지 않는다 — 호출하는 module이 자기 API 계약에 맞춰 정한다.
     * @throws IllegalArgumentException {@code size}가 1 미만이거나 {@code targetType}이 허용되지 않는 값일 때
     */
    CursorPage<LikeSnapshot, Long> findLiked(String targetType, long memberId, @Nullable Long cursorLikeId, int size);
}
