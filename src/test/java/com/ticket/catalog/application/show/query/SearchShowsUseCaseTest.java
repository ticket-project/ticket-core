package com.ticket.catalog.application.show.query;

import com.ticket.catalog.domain.show.Region;
import com.ticket.catalog.application.show.query.model.ShowSearchCriteria;
import com.ticket.catalog.application.show.query.model.ShowSearchItemView;
import com.ticket.catalog.application.show.query.model.ShowCursor;
import com.ticket.shared.CursorPage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
class SearchShowsUseCaseTest {

    private static final ShowCursor NEXT_POSITION =
            new ShowCursor(ShowSort.POPULAR, "DESC", "10", 1L);

    @Mock
    private ShowListReadRepository showListReadRepository;

    @InjectMocks
    private SearchShowsUseCase useCase;

    @Test
    void 검색_결과와_커서를_반환한다() {
        ShowSearchCriteria request = new ShowSearchCriteria("concert", null, null, null, null, null, null);
        ShowSearchItemView item = new ShowSearchItemView(
                1L,
                "concert",
                "image",
                "venue",
                LocalDate.of(2026, 3, 27),
                LocalDate.of(2026, 3, 28),
                Region.SEOUL,
                10L
        );
        CursorPage<ShowSearchItemView, ShowCursor> result = new CursorPage<>(List.of(item), true, NEXT_POSITION);
        when(showListReadRepository.searchShows(request, 20, ShowSort.POPULAR)).thenReturn(result);

        SearchShowsUseCase.Output output = useCase.execute(new SearchShowsUseCase.Input(request, 20, ShowSort.from("popular")));

        assertThat(output.items()).containsExactly(item);
        assertThat(output.nextPosition()).isEqualTo(NEXT_POSITION);
        assertThat(output.hasNext()).isTrue();
        verify(showListReadRepository).searchShows(request, 20, ShowSort.POPULAR);
    }

    @Test
    void 검색_결과가_없으면_빈_슬라이스와_null_커서를_반환한다() {
        ShowSearchCriteria request = new ShowSearchCriteria("missing", null, null, null, null, null, null);
        CursorPage<ShowSearchItemView, ShowCursor> result = new CursorPage<>(List.of(), false, null);
        when(showListReadRepository.searchShows(request, 20, ShowSort.POPULAR)).thenReturn(result);

        SearchShowsUseCase.Output output = useCase.execute(new SearchShowsUseCase.Input(request, 20, ShowSort.from("popular")));

        assertThat(output.items()).isEmpty();
        assertThat(output.nextPosition()).isNull();
        assertThat(output.hasNext()).isFalse();
        verify(showListReadRepository).searchShows(request, 20, ShowSort.POPULAR);
    }
}
