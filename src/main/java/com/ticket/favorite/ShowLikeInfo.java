package com.ticket.favorite;

/**
 * 특정 공연·회원 조합의 찜 상태 스냅샷이다. {@code likeCount}는 그 공연의 전체 찜 개수다.
 */
public record ShowLikeInfo(boolean liked, long likeCount) {
}
