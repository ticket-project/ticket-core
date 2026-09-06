package com.ticket.catalog.application.show.query;

import com.ticket.catalog.application.show.query.model.SaleOpeningSoonSearchParam;
import com.ticket.catalog.application.show.query.model.ShowCursor;
import com.ticket.catalog.application.show.query.model.ShowOpeningSoonDetailView;
import com.ticket.error.InvalidRequestException;
import com.ticket.shared.CursorPage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class GetSaleStartApproachingShowsPageUseCase {

    private final ShowListReadRepository showListReadRepository;

    public record Input(SaleOpeningSoonSearchParam param, int size, ShowSort sort) {
        public Input {
            if (param == null) {
                throw new InvalidRequestException("param는 필수입니다.");
            }
            if (sort == null) {
                throw new InvalidRequestException("sort는 필수입니다.");
            }
            if (size <= 0) {
                throw new InvalidRequestException("size는 1 이상이어야 합니다.");
            }
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
