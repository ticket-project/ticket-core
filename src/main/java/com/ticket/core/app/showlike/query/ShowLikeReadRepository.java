package com.ticket.core.app.showlike.query;

import com.ticket.core.app.support.cursor.CursorPage;
import com.ticket.core.app.showlike.query.model.ShowLikeSummaryView;

/**
 * 내 찜 목록 읽기 전용 조회 포트다.
 *
 * <p>커서 위치는 마지막 찜 id다. wire 문자열 변환은 core-api가 한다.
 */
public interface ShowLikeReadRepository {

    CursorPage<ShowLikeSummaryView, Long> findMyLikedShows(
            Long memberId,
            Long cursorLikeId,
            int size
    );
}
