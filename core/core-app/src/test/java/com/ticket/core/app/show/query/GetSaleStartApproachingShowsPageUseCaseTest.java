package com.ticket.core.app.show.query;

import com.ticket.core.domain.show.meta.Region;
import com.ticket.core.app.show.query.model.SaleOpeningSoonSearchParam;
import com.ticket.core.app.show.query.model.ShowOpeningSoonDetailView;
import com.ticket.core.app.support.cursor.CursorSlice;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.SliceImpl;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
class GetSaleStartApproachingShowsPageUseCaseTest {

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
        CursorSlice<ShowOpeningSoonDetailView> result =
                new CursorSlice<>(new SliceImpl<>(List.of(show)), "next-cursor");
        when(showListReadRepository.findSaleOpeningSoonPage(param, 10, "popular")).thenReturn(result);

        GetSaleStartApproachingShowsPageUseCase.Output output =
                useCase.execute(new GetSaleStartApproachingShowsPageUseCase.Input(param, 10, "popular"));

        assertThat(output.shows().getContent()).containsExactly(show);
        assertThat(output.nextCursor()).isEqualTo("next-cursor");
        verify(showListReadRepository).findSaleOpeningSoonPage(param, 10, "popular");
    }

    @Test
    void 판매_오픈예정_공연이_없으면_빈_슬라이스와_null_커서를_반환한다() {
        SaleOpeningSoonSearchParam param = new SaleOpeningSoonSearchParam(null, null, null, null, null, null, null, null);
        CursorSlice<ShowOpeningSoonDetailView> result =
                new CursorSlice<>(new SliceImpl<>(List.of()), null);
        when(showListReadRepository.findSaleOpeningSoonPage(param, 10, "popular")).thenReturn(result);

        GetSaleStartApproachingShowsPageUseCase.Output output =
                useCase.execute(new GetSaleStartApproachingShowsPageUseCase.Input(param, 10, "popular"));

        assertThat(output.shows().getContent()).isEmpty();
        assertThat(output.nextCursor()).isNull();
        verify(showListReadRepository).findSaleOpeningSoonPage(param, 10, "popular");
    }
}
