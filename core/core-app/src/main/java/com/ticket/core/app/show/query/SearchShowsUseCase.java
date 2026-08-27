package com.ticket.core.app.show.query;

import com.ticket.core.app.show.query.model.ShowSearchCriteria;
import com.ticket.core.app.show.query.model.ShowSearchItemView;
import com.ticket.core.app.support.cursor.CursorSlice;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class SearchShowsUseCase {
    private final ShowListReadRepository showListReadRepository;

    public record Input(ShowSearchCriteria request, int size, ShowSort sort) {
    }

    public record Output(Slice<ShowSearchItemView> shows, String nextCursor) {
    }

    public Output execute(final Input input) {
        final CursorSlice<ShowSearchItemView> result = showListReadRepository.searchShows(
                input.request(), input.size(), input.sort().apiValue());
        return new Output(result.slice(), result.nextCursor());
    }
}
