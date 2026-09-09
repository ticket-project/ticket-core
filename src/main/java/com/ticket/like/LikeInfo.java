package com.ticket.like;

/**
 * 특정 대상·회원 조합의 찜 상태 스냅샷이다. {@code likeCount}는 그 대상의 전체 찜 개수다.
 */
public record LikeInfo(boolean liked, long likeCount) {
}
