package com.ticket.show.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
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
import com.ticket.show.query.SaleOpeningSoonDetailRow;
import com.ticket.show.query.SaleOpeningSoonSearchParam;
import com.ticket.show.query.ShowCursor;
import com.ticket.show.query.ShowSort;
import com.ticket.show.usecase.view.SaleOpeningSoonDetailView;
import com.ticket.venue.api.Region;
import com.ticket.venue.api.VenueLookupApi;
import com.ticket.venue.api.VenueSummary;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
class GetSaleOpeningSoonShowsPageUseCaseTest {
    private static final ShowCursor NEXT_POSITION =
            new ShowCursor(ShowSort.POPULAR, "DESC", "10", 1L);
    @Mock private ShowQueryRepository showQueryRepository;
    @Mock private VenueLookupApi venueLookup;
    @InjectMocks private GetSaleOpeningSoonShowsPageUseCase useCase;

    @Test
    void 커서_페이지_응답을_output으로_변환한다() {
        SaleOpeningSoonSearchParam param =
                new SaleOpeningSoonSearchParam(null, null, null, null, null, null, null, null);
        LocalDate startDate = LocalDate.of(2026, 3, 27);
        LocalDate endDate = LocalDate.of(2026, 3, 28);
        LocalDateTime saleStartDate = LocalDateTime.of(2026, 3, 27, 10, 0);
        LocalDateTime saleEndDate = LocalDateTime.of(2026, 3, 28, 10, 0);

        SaleOpeningSoonDetailRow row =
                new SaleOpeningSoonDetailRow(
                        1L,
                        "concert",
                        "subtitle",
                        "image",
                        startDate,
                        endDate,
                        saleStartDate,
                        saleEndDate,
                        100L,
                        7L);
        CursorPage<SaleOpeningSoonDetailRow, ShowCursor> result =
                new CursorPage<>(List.of(row), true, NEXT_POSITION);
        when(showQueryRepository.findSaleOpeningSoonPage(param, null, 10, ShowSort.POPULAR))
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

        GetSaleOpeningSoonShowsPageUseCase.Output output =
                useCase.execute(
                        new GetSaleOpeningSoonShowsPageUseCase.Input(param, 10, ShowSort.POPULAR));

        assertThat(output.items())
                .containsExactly(
                        new SaleOpeningSoonDetailView(
                                1L,
                                "concert",
                                "subtitle",
                                "image",
                                "venue",
                                Region.SEOUL,
                                startDate,
                                endDate,
                                saleStartDate,
                                saleEndDate,
                                100L));
        assertThat(output.nextPosition()).isEqualTo(NEXT_POSITION);
        assertThat(output.hasNext()).isTrue();
        verify(showQueryRepository).findSaleOpeningSoonPage(param, null, 10, ShowSort.POPULAR);
    }

    @Test
    void 판매_오픈예정_공연이_없으면_빈_슬라이스와_null_커서를_반환한다() {
        SaleOpeningSoonSearchParam param =
                new SaleOpeningSoonSearchParam(null, null, null, null, null, null, null, null);
        CursorPage<SaleOpeningSoonDetailRow, ShowCursor> result =
                new CursorPage<>(List.of(), false, null);
        when(showQueryRepository.findSaleOpeningSoonPage(param, null, 10, ShowSort.POPULAR))
                .thenReturn(result);

        GetSaleOpeningSoonShowsPageUseCase.Output output =
                useCase.execute(
                        new GetSaleOpeningSoonShowsPageUseCase.Input(param, 10, ShowSort.POPULAR));

        assertThat(output.items()).isEmpty();
        assertThat(output.nextPosition()).isNull();
        assertThat(output.hasNext()).isFalse();
        verify(showQueryRepository).findSaleOpeningSoonPage(param, null, 10, ShowSort.POPULAR);
    }

    /**
     * 지역 미지정({@code null})과 그 지역에 공연장이 없음(빈 집합)은 다른 조건이다. 뭉개면 "그 지역에 공연장이 없다"가 "전체 목록"으로 조용히 바뀐다.
     */
    @Test
    void 지역_미지정과_지역_공연장_0건을_구분해_넘긴다() {
        CursorPage<SaleOpeningSoonDetailRow, ShowCursor> empty =
                new CursorPage<>(List.of(), false, null);
        SaleOpeningSoonSearchParam noRegion =
                new SaleOpeningSoonSearchParam(null, null, null, null, null, null, null, null);
        SaleOpeningSoonSearchParam jeju =
                new SaleOpeningSoonSearchParam(
                        null, null, Region.JEJU, null, null, null, null, null);
        when(venueLookup.findIdsByRegion(Region.JEJU)).thenReturn(Set.of());
        when(showQueryRepository.findSaleOpeningSoonPage(noRegion, null, 10, ShowSort.POPULAR))
                .thenReturn(empty);
        when(showQueryRepository.findSaleOpeningSoonPage(jeju, Set.of(), 10, ShowSort.POPULAR))
                .thenReturn(empty);

        useCase.execute(
                new GetSaleOpeningSoonShowsPageUseCase.Input(noRegion, 10, ShowSort.POPULAR));
        useCase.execute(new GetSaleOpeningSoonShowsPageUseCase.Input(jeju, 10, ShowSort.POPULAR));

        verify(showQueryRepository).findSaleOpeningSoonPage(noRegion, null, 10, ShowSort.POPULAR);
        verify(showQueryRepository).findSaleOpeningSoonPage(jeju, Set.of(), 10, ShowSort.POPULAR);
    }
}
