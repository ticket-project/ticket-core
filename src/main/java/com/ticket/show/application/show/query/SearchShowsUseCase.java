package com.ticket.show.application.show.query;

import com.ticket.show.application.show.query.model.ShowCursor;
import com.ticket.show.application.show.query.model.ShowSearchCriteria;
import com.ticket.show.application.show.query.model.ShowSearchItemView;
import com.ticket.error.InvalidRequestException;
import com.ticket.shared.CursorPage;
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
        public Input {
            if (request == null) {
                throw new InvalidRequestException("request는 필수입니다.");
            }
            if (sort == null) {
                throw new InvalidRequestException("sort는 필수입니다.");
            }
            if (size <= 0) {
                throw new InvalidRequestException("size는 1 이상이어야 합니다.");
            }
        }
    }

    public record Output(List<ShowSearchItemView> items, boolean hasNext, ShowCursor nextPosition) {
    }

    public Output execute(final Input input) {
        final CursorPage<ShowSearchItemView, ShowCursor> page = showListReadRepository.searchShows(
                input.request(), input.size(), input.sort());
        return new Output(page.items(), page.hasNext(), page.nextPosition());
    }
}
