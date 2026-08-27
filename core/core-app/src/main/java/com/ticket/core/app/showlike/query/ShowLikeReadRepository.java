package com.ticket.core.app.showlike.query;

import com.ticket.core.app.support.cursor.CursorSlice;

public interface ShowLikeReadRepository {

    CursorSlice<GetMyShowLikesUseCase.ShowLikeSummary> findMyLikedShows(
            Long memberId,
            Long cursorLikeId,
            int size
    );
}
