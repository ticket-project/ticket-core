package com.ticket.favorite;

import java.time.LocalDateTime;

/**
 * 내 찜 목록 한 항목이다. show 표시값(제목·이미지·공연장 이름 등)은 담지 않는다 — 호출자가
 * {@link #showId()}로 자기 데이터를 다시 조회해 조립한다.
 */
public record ShowLikeEntry(long likeId, long showId, LocalDateTime likedAt) {
}
