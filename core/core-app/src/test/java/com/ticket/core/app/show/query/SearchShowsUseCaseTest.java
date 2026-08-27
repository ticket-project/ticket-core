package com.ticket.core.app.show.query;

import com.ticket.core.domain.show.meta.Region;
import com.ticket.core.app.show.query.model.ShowSearchCriteria;
import com.ticket.core.app.show.query.model.ShowSearchItemView;
import com.ticket.core.app.support.cursor.CursorSlice;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.SliceImpl;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
class SearchShowsUseCaseTest {

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
        CursorSlice<ShowSearchItemView> result = new CursorSlice<>(new SliceImpl<>(List.of(item)), "next");
        when(showListReadRepository.searchShows(request, 20, "popular")).thenReturn(result);

        SearchShowsUseCase.Output output = useCase.execute(new SearchShowsUseCase.Input(request, 20, ShowSort.from("popular")));

        assertThat(output.shows().getContent()).containsExactly(item);
        assertThat(output.nextCursor()).isEqualTo("next");
        verify(showListReadRepository).searchShows(request, 20, "popular");
    }

    @Test
    void 검색_결과가_없으면_빈_슬라이스와_null_커서를_반환한다() {
        ShowSearchCriteria request = new ShowSearchCriteria("missing", null, null, null, null, null, null);
        CursorSlice<ShowSearchItemView> result = new CursorSlice<>(new SliceImpl<>(List.of()), null);
        when(showListReadRepository.searchShows(request, 20, "popular")).thenReturn(result);

        SearchShowsUseCase.Output output = useCase.execute(new SearchShowsUseCase.Input(request, 20, ShowSort.from("popular")));

        assertThat(output.shows().getContent()).isEmpty();
        assertThat(output.nextCursor()).isNull();
        verify(showListReadRepository).searchShows(request, 20, "popular");
    }
}
