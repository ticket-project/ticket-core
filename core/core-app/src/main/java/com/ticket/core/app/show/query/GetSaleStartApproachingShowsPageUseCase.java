package com.ticket.core.app.show.query;

import com.ticket.core.app.show.query.model.SaleOpeningSoonSearchParam;
import com.ticket.core.app.show.query.model.ShowCursor;
import com.ticket.core.app.show.query.model.ShowOpeningSoonDetailView;
import com.ticket.core.app.support.cursor.CursorPage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import com.ticket.core.app.support.validation.RequiredInput;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class GetSaleStartApproachingShowsPageUseCase {

    private final ShowListReadRepository showListReadRepository;

    public record Input(SaleOpeningSoonSearchParam param, int size, String sort) {
        public Input {
            RequiredInput.notNull(param, "param");
            RequiredInput.positiveSize(size, "size");
        }
    }

    public record Output(List<ShowOpeningSoonDetailView> items, boolean hasNext, ShowCursor nextPosition) {
    }

    public Output execute(final Input input) {
        final CursorPage<ShowOpeningSoonDetailView, ShowCursor> page =
                showListReadRepository.findSaleOpeningSoonPage(
                        input.param(),
                        input.size(),
                        input.sort()
                );
        return new Output(page.items(), page.hasNext(), page.nextPosition());
    }
}
