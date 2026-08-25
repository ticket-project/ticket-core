package com.ticket.core.app.showlike.query;

import com.ticket.core.support.cursor.CursorSlice;

public interface ShowLikeQueryRepository {

    CursorSlice<GetMyShowLikesUseCase.ShowLikeSummary> findMyLikedShows(
            Long memberId,
            Long cursorLikeId,
            int size
    );
}
