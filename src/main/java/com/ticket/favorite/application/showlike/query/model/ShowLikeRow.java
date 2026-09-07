package com.ticket.favorite.application.showlike.query.model;

import java.time.LocalDateTime;

/**
 * 찜 목록 조회 한 행이다. show 표시값은 담지 않는다 — favorite는 showId만 안다.
 */
public record ShowLikeRow(long likeId, long showId, LocalDateTime likedAt) {
}
