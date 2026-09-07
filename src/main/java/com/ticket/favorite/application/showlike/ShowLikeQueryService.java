package com.ticket.favorite.application.showlike;

import com.ticket.favorite.ShowLikeEntry;
import com.ticket.favorite.ShowLikeInfo;
import com.ticket.favorite.ShowLikeQuery;
import com.ticket.favorite.application.showlike.query.ShowLikeReadRepository;
import com.ticket.favorite.application.showlike.query.model.ShowLikeRow;
import com.ticket.favorite.domain.showlike.repository.ShowLikeRepository;
import com.ticket.shared.CursorPage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link ShowLikeQuery}의 favorite 소유 구현이다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ShowLikeQueryService implements ShowLikeQuery {

    private final ShowLikeRepository showLikeRepository;
    private final ShowLikeReadRepository showLikeReadRepository;

    @Override
    public ShowLikeInfo getShowLike(final long showId, final long memberId) {
        final boolean liked = showLikeRepository.existsByMemberIdAndShowId(memberId, showId);
        final long likeCount = showLikeRepository.countByShowId(showId);
        return new ShowLikeInfo(liked, likeCount);
    }

    @Override
    public long countByShowId(final long showId) {
        return showLikeRepository.countByShowId(showId);
    }

    @Override
    public CursorPage<ShowLikeEntry, Long> findLikedShows(final long memberId, final Long cursorLikeId, final int size) {
        final CursorPage<ShowLikeRow, Long> page = showLikeReadRepository.findLikedShows(memberId, cursorLikeId, size);
        final java.util.List<ShowLikeEntry> entries = page.items().stream()
                .map(row -> new ShowLikeEntry(row.likeId(), row.showId(), row.likedAt()))
                .toList();
        return new CursorPage<>(entries, page.hasNext(), page.nextPosition());
    }
}
