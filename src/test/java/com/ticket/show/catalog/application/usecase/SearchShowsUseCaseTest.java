package com.ticket.show.catalog.application.usecase;

import com.ticket.show.catalog.application.ShowSearchItemRow;
import com.ticket.show.catalog.application.ShowSort;

import com.ticket.show.catalog.application.port.ShowListQueryPort;

import com.ticket.venue.Region;
import com.ticket.venue.VenueLookup;
import com.ticket.venue.VenueSummary;
import com.ticket.show.catalog.application.ShowSearchCriteria;
import com.ticket.show.catalog.application.ShowSearchItemView;
import com.ticket.show.catalog.application.ShowCursor;
import com.ticket.shared.CursorPage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
class SearchShowsUseCaseTest {

    private static final ShowCursor NEXT_POSITION =
            new ShowCursor(ShowSort.POPULAR, "DESC", "10", 1L);

    @Mock
    private ShowListQueryPort showListQueryPort;

    @Mock
    private VenueLookup venueLookup;

    @InjectMocks
    private SearchShowsUseCase useCase;

    @Test
    void 검색_결과와_커서를_반환한다() {
        ShowSearchCriteria request = new ShowSearchCriteria("concert", null, null, null, null, null, null);
        ShowSearchItemRow row = new ShowSearchItemRow(
                1L,
                "concert",
                "image",
                LocalDate.of(2026, 3, 27),
                LocalDate.of(2026, 3, 28),
                10L,
                7L
        );
        CursorPage<ShowSearchItemRow, ShowCursor> result = new CursorPage<>(List.of(row), true, NEXT_POSITION);
        when(showListQueryPort.searchShows(request, 20, ShowSort.POPULAR)).thenReturn(result);
        when(venueLookup.getSummaries(Set.of(7L))).thenReturn(Map.of(7L, new VenueSummary(
                7L, "venue", "주소", Region.SEOUL, null, null, null, null,
                new VenueSummary.SeatMapLayout(0, 0, 0.0)
        )));

        SearchShowsUseCase.Output output = useCase.execute(new SearchShowsUseCase.Input(request, 20, ShowSort.from("popular")));

        assertThat(output.items()).containsExactly(new ShowSearchItemView(
                1L, "concert", "image", "venue",
                LocalDate.of(2026, 3, 27), LocalDate.of(2026, 3, 28), Region.SEOUL, 10L
        ));
        assertThat(output.nextPosition()).isEqualTo(NEXT_POSITION);
        assertThat(output.hasNext()).isTrue();
        verify(showListQueryPort).searchShows(request, 20, ShowSort.POPULAR);
    }

    @Test
    void 검색_결과가_없으면_빈_슬라이스와_null_커서를_반환한다() {
        ShowSearchCriteria request = new ShowSearchCriteria("missing", null, null, null, null, null, null);
        CursorPage<ShowSearchItemRow, ShowCursor> result = new CursorPage<>(List.of(), false, null);
        when(showListQueryPort.searchShows(request, 20, ShowSort.POPULAR)).thenReturn(result);

        SearchShowsUseCase.Output output = useCase.execute(new SearchShowsUseCase.Input(request, 20, ShowSort.from("popular")));

        assertThat(output.items()).isEmpty();
        assertThat(output.nextPosition()).isNull();
        assertThat(output.hasNext()).isFalse();
        verify(showListQueryPort).searchShows(request, 20, ShowSort.POPULAR);
    }
}
