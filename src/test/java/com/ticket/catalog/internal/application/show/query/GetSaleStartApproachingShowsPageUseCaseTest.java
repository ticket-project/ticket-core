package com.ticket.catalog.internal.application.show.query;

import com.ticket.catalog.internal.domain.show.Region;
import com.ticket.catalog.internal.application.show.query.model.SaleOpeningSoonSearchParam;
import com.ticket.catalog.internal.application.show.query.model.ShowOpeningSoonDetailView;
import com.ticket.catalog.internal.application.show.query.model.ShowCursor;
import com.ticket.shared.CursorPage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
class GetSaleStartApproachingShowsPageUseCaseTest {

    private static final ShowCursor NEXT_POSITION =
            new ShowCursor(ShowSort.POPULAR, "DESC", "10", 1L);

    @Mock
    private ShowListReadRepository showListReadRepository;

    @InjectMocks
    private GetSaleStartApproachingShowsPageUseCase useCase;

    @Test
    void 커서_페이지_응답을_output으로_변환한다() {
        SaleOpeningSoonSearchParam param = new SaleOpeningSoonSearchParam(null, null, null, null, null, null, null, null);
        LocalDate startDate = LocalDate.of(2026, 3, 27);
        LocalDate endDate = LocalDate.of(2026, 3, 28);
        LocalDateTime saleStartDate = LocalDateTime.of(2026, 3, 27, 10, 0);
        LocalDateTime saleEndDate = LocalDateTime.of(2026, 3, 28, 10, 0);

        ShowOpeningSoonDetailView show = new ShowOpeningSoonDetailView(
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
                100L
        );
        CursorPage<ShowOpeningSoonDetailView, ShowCursor> result =
                new CursorPage<>(List.of(show), true, NEXT_POSITION);
        when(showListReadRepository.findSaleOpeningSoonPage(param, 10, ShowSort.POPULAR)).thenReturn(result);

        GetSaleStartApproachingShowsPageUseCase.Output output =
                useCase.execute(new GetSaleStartApproachingShowsPageUseCase.Input(param, 10, ShowSort.POPULAR));

        assertThat(output.items()).containsExactly(show);
        assertThat(output.nextPosition()).isEqualTo(NEXT_POSITION);
        assertThat(output.hasNext()).isTrue();
        verify(showListReadRepository).findSaleOpeningSoonPage(param, 10, ShowSort.POPULAR);
    }

    @Test
    void 판매_오픈예정_공연이_없으면_빈_슬라이스와_null_커서를_반환한다() {
        SaleOpeningSoonSearchParam param = new SaleOpeningSoonSearchParam(null, null, null, null, null, null, null, null);
        CursorPage<ShowOpeningSoonDetailView, ShowCursor> result =
                new CursorPage<>(List.of(), false, null);
        when(showListReadRepository.findSaleOpeningSoonPage(param, 10, ShowSort.POPULAR)).thenReturn(result);

        GetSaleStartApproachingShowsPageUseCase.Output output =
                useCase.execute(new GetSaleStartApproachingShowsPageUseCase.Input(param, 10, ShowSort.POPULAR));

        assertThat(output.items()).isEmpty();
        assertThat(output.nextPosition()).isNull();
        assertThat(output.hasNext()).isFalse();
        verify(showListReadRepository).findSaleOpeningSoonPage(param, 10, ShowSort.POPULAR);
    }
}
