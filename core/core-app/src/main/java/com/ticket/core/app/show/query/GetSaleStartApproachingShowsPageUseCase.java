package com.ticket.core.app.show.query;

import com.ticket.core.app.show.query.model.SaleOpeningSoonSearchParam;
import com.ticket.core.app.show.query.model.ShowOpeningSoonDetailView;
import com.ticket.core.app.support.cursor.CursorSlice;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class GetSaleStartApproachingShowsPageUseCase {

    private final ShowListQueryRepository showListQueryRepository;

    public record Input(SaleOpeningSoonSearchParam param, int size, String sort) {
    }

    public record Output(Slice<ShowOpeningSoonDetailView> shows, String nextCursor) {
    }

    public Output execute(final Input input) {
        final CursorSlice<ShowOpeningSoonDetailView> result = showListQueryRepository.findSaleOpeningSoonPage(
                input.param(),
                input.size(),
                input.sort()
        );
        return new Output(result.slice(), result.nextCursor());
    }
}
