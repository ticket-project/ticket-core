package com.ticket.catalog.internal.application.show.query;

import com.ticket.catalog.internal.application.show.query.model.ShowCursor;
import com.ticket.catalog.internal.application.show.query.model.ShowListItemView;
import com.ticket.catalog.internal.application.show.query.model.ShowParam;
import com.ticket.core.app.support.cursor.CursorPage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import com.ticket.core.app.support.validation.RequiredInput;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class GetShowsUseCase {

    /**
     * API 문서가 공개한 상한이다(`한 번에 조회할 개수 (기본값: 5, 최대: 100)`).
     * 상한은 유스케이스 조건이므로 API가 아니라 여기가 소유한다.
     */
    public static final int MAX_SIZE = 100;

    private final ShowListReadRepository showListReadRepository;

    public record Input(ShowParam param, int size, ShowSort sort) {
        public Input {
            RequiredInput.notNull(param, "param");
            RequiredInput.notNull(sort, "sort");
            RequiredInput.sizeWithin(size, MAX_SIZE, "size");
        }
    }

    public record Output(List<ShowListItemView> items, boolean hasNext, ShowCursor nextPosition) {
    }

    public Output execute(final Input input) {
        final CursorPage<ShowListItemView, ShowCursor> page = showListReadRepository.findAllBySearch(
                input.param(), input.size(), input.sort());
        return new Output(page.items(), page.hasNext(), page.nextPosition());
    }
}
