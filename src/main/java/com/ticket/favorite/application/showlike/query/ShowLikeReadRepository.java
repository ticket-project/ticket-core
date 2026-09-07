package com.ticket.favorite.application.showlike.query;

import com.ticket.favorite.application.showlike.query.model.ShowLikeRow;
import com.ticket.shared.CursorPage;

/**
 * 찜 목록 읽기 전용 조회 포트다.
 *
 * <p>커서 위치는 마지막 찜 id다. wire 문자열 변환은 호출하는 module의 {@code web}이 한다.
 */
public interface ShowLikeReadRepository {

    CursorPage<ShowLikeRow, Long> findLikedShows(
            Long memberId,
            Long cursorLikeId,
            int size
    );
}
