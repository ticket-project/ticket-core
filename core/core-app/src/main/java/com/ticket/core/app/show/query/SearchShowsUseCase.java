package com.ticket.core.app.show.query;

import com.ticket.core.app.show.query.model.ShowCursor;
import com.ticket.core.app.show.query.model.ShowSearchCriteria;
import com.ticket.core.app.show.query.model.ShowSearchItemView;
import com.ticket.core.app.support.cursor.CursorPage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class SearchShowsUseCase {
    private final ShowListReadRepository showListReadRepository;

    public record Input(ShowSearchCriteria request, int size, ShowSort sort) {
    }

    public record Output(List<ShowSearchItemView> items, boolean hasNext, ShowCursor nextPosition) {
    }

    public Output execute(final Input input) {
        final CursorPage<ShowSearchItemView, ShowCursor> page = showListReadRepository.searchShows(
                input.request(), input.size(), input.sort().apiValue());
        return new Output(page.items(), page.hasNext(), page.nextPosition());
    }
}
