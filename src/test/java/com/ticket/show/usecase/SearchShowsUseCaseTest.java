package com.ticket.show.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ticket.shared.api.CursorPage;
import com.ticket.show.persistence.ShowQueryRepository;
import com.ticket.show.query.ShowCursor;
import com.ticket.show.query.ShowSearchCriteria;
import com.ticket.show.query.ShowSearchItemRow;
import com.ticket.show.query.ShowSort;
import com.ticket.show.usecase.view.ShowSearchItemView;
import com.ticket.venue.api.Region;
import com.ticket.venue.api.VenueLookupApi;
import com.ticket.venue.api.VenueSummary;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
class SearchShowsUseCaseTest {
    private static final ShowCursor NEXT_POSITION =
            new ShowCursor(ShowSort.POPULAR, "DESC", "10", 1L);
    @Mock private ShowQueryRepository showQueryRepository;
    @Mock private VenueLookupApi venueLookup;
    @InjectMocks private SearchShowsUseCase useCase;

    @Test
    void 검색_결과와_커서를_반환한다() {
        ShowSearchCriteria request =
                new ShowSearchCriteria("concert", null, null, null, null, null, null);
        ShowSearchItemRow row =
                new ShowSearchItemRow(
                        1L,
                        "concert",
                        "image",
                        LocalDate.of(2026, 3, 27),
                        LocalDate.of(2026, 3, 28),
                        10L,
                        7L);
        CursorPage<ShowSearchItemRow, ShowCursor> result =
                new CursorPage<>(List.of(row), true, NEXT_POSITION);
        when(showQueryRepository.searchShows(request, null, 20, ShowSort.POPULAR))
                .thenReturn(result);
        when(venueLookup.getSummaries(Set.of(7L)))
                .thenReturn(
                        Map.of(
                                7L,
                                new VenueSummary(
                                        7L,
                                        "venue",
                                        "주소",
                                        Region.SEOUL,
                                        null,
                                        null,
                                        null,
                                        null,
                                        new VenueSummary.SeatMapLayout(0, 0, 0.0))));

        SearchShowsUseCase.Output output =
                useCase.execute(
                        new SearchShowsUseCase.Input(request, 20, ShowSort.from("popular")));

        assertThat(output.items())
                .containsExactly(
                        new ShowSearchItemView(
                                1L,
                                "concert",
                                "image",
                                "venue",
                                LocalDate.of(2026, 3, 27),
                                LocalDate.of(2026, 3, 28),
                                Region.SEOUL,
                                10L));
        assertThat(output.nextPosition()).isEqualTo(NEXT_POSITION);
        assertThat(output.hasNext()).isTrue();
        verify(showQueryRepository).searchShows(request, null, 20, ShowSort.POPULAR);
    }

    @Test
    void 검색_결과가_없으면_빈_슬라이스와_null_커서를_반환한다() {
        ShowSearchCriteria request =
                new ShowSearchCriteria("missing", null, null, null, null, null, null);
        CursorPage<ShowSearchItemRow, ShowCursor> result = new CursorPage<>(List.of(), false, null);
        when(showQueryRepository.searchShows(request, null, 20, ShowSort.POPULAR))
                .thenReturn(result);

        SearchShowsUseCase.Output output =
                useCase.execute(
                        new SearchShowsUseCase.Input(request, 20, ShowSort.from("popular")));

        assertThat(output.items()).isEmpty();
        assertThat(output.nextPosition()).isNull();
        assertThat(output.hasNext()).isFalse();
        verify(showQueryRepository).searchShows(request, null, 20, ShowSort.POPULAR);
    }

    /**
     * 지역 미지정({@code null})과 그 지역에 공연장이 없음(빈 집합)은 다른 조건이다. 뭉개면 "그 지역에 공연장이 없다"가 "전체 목록"으로 조용히 바뀐다.
     */
    @Test
    void 지역_미지정과_지역_공연장_0건을_구분해_넘긴다() {
        CursorPage<ShowSearchItemRow, ShowCursor> empty = new CursorPage<>(List.of(), false, null);
        ShowSearchCriteria noRegion =
                new ShowSearchCriteria(null, null, null, null, null, null, null);
        ShowSearchCriteria jeju =
                new ShowSearchCriteria(null, null, null, null, null, Region.JEJU, null);
        when(venueLookup.findIdsByRegion(Region.JEJU)).thenReturn(Set.of());
        when(showQueryRepository.searchShows(noRegion, null, 10, ShowSort.POPULAR))
                .thenReturn(empty);
        when(showQueryRepository.searchShows(jeju, Set.of(), 10, ShowSort.POPULAR))
                .thenReturn(empty);

        useCase.execute(new SearchShowsUseCase.Input(noRegion, 10, ShowSort.POPULAR));
        useCase.execute(new SearchShowsUseCase.Input(jeju, 10, ShowSort.POPULAR));

        verify(showQueryRepository).searchShows(noRegion, null, 10, ShowSort.POPULAR);
        verify(showQueryRepository).searchShows(jeju, Set.of(), 10, ShowSort.POPULAR);
    }
}
