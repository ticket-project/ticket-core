package com.ticket.catalog.application.showlike.query;

import com.ticket.catalog.application.showlike.query.model.ShowLikeSummaryView;
import com.ticket.shared.CursorPage;

/**
 * 내 찜 목록 읽기 전용 조회 포트다.
 *
 * <p>커서 위치는 마지막 찜 id다. wire 문자열 변환은 이 module의 {@code web}이 한다.
 */
public interface ShowLikeReadRepository {

    CursorPage<ShowLikeSummaryView, Long> findMyLikedShows(
            Long memberId,
            Long cursorLikeId,
            int size
    );
}
