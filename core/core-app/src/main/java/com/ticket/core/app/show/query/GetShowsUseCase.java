package com.ticket.core.app.show.query;

import com.ticket.core.domain.show.meta.Region;
import com.ticket.core.domain.show.meta.SaleType;
import com.ticket.core.app.show.query.model.ShowListItemView;
import com.ticket.core.app.show.query.model.ShowParam;
import com.ticket.core.app.support.cursor.CursorSlice;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class GetShowsUseCase {
    private final ShowListQueryRepository showListQueryRepository;

    public record Input(ShowParam param, int size, ShowSort sort) {
    }

    public record Output(Slice<ShowListItemView> shows, String nextCursor) {
    }

    public Output execute(final Input input) {
        final CursorSlice<ShowListItemView> result = showListQueryRepository.findAllBySearch(
                input.param(), input.size(), input.sort().apiValue());
        return new Output(result.slice(), result.nextCursor());
    }
}
