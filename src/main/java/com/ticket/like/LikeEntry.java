package com.ticket.like;

import java.time.LocalDateTime;

/**
 * 내 찜 목록 한 항목이다. 대상 표시값(공연이면 제목·이미지·공연장 이름 등)은 담지 않는다 —
 * 호출자가 {@link #targetId()}로 자기 데이터를 다시 조회해 조립한다.
 */
public record LikeEntry(long likeId, long targetId, LocalDateTime likedAt) {
}
