package com.ticket.core.app.show.query;

import com.ticket.core.app.show.query.model.ShowCursor;
import com.ticket.core.app.show.query.model.ShowListItemView;
import com.ticket.core.app.show.query.model.ShowParam;
import com.ticket.core.app.support.cursor.CursorPage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class GetShowsUseCase {
    private final ShowListReadRepository showListReadRepository;

    public record Input(ShowParam param, int size, ShowSort sort) {
    }

    public record Output(List<ShowListItemView> items, boolean hasNext, ShowCursor nextPosition) {
    }

    public Output execute(final Input input) {
        final CursorPage<ShowListItemView, ShowCursor> page = showListReadRepository.findAllBySearch(
                input.param(), input.size(), input.sort().apiValue());
        return new Output(page.items(), page.hasNext(), page.nextPosition());
    }
}
