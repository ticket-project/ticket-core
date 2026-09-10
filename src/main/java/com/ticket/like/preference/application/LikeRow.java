package com.ticket.like.preference.application;

import java.time.LocalDateTime;

/**
 * 찜 목록 조회 한 행이다. 대상 표시값은 담지 않는다 — like는 targetId만 안다.
 */
public record LikeRow(long likeId, long targetId, LocalDateTime likedAt) {
}
