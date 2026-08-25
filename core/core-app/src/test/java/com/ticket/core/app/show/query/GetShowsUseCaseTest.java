package com.ticket.core.app.show.query;

import com.ticket.core.domain.show.meta.SaleType;
import com.ticket.core.app.show.query.model.ShowListItemView;
import com.ticket.core.app.show.query.model.ShowParam;
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
class GetShowsUseCaseTest {

    @Mock
    private ShowListQueryRepository showListQueryRepository;

    @InjectMocks
    private GetShowsUseCase useCase;

    @Test
    void 공연_목록과_커서를_반환한다() {
        ShowParam param = new ShowParam(null, null, null, null);
        LocalDate startDate = LocalDate.of(2026, 3, 27);
        LocalDate endDate = LocalDate.of(2026, 3, 28);
        LocalDateTime saleStartDate = LocalDateTime.of(2026, 3, 20, 10, 0);
        LocalDateTime saleEndDate = LocalDateTime.of(2026, 3, 28, 10, 0);
        LocalDateTime createdAt = LocalDateTime.of(2026, 3, 1, 10, 0);

        ShowListItemView show = new ShowListItemView(
                1L,
                "concert",
                "subtitle",
                "image",
                List.of("rock"),
                startDate,
                endDate,
                10L,
                SaleType.GENERAL,
                saleStartDate,
                saleEndDate,
                createdAt,
                null,
                "venue"
        );
        CursorSlice<ShowListItemView> result = new CursorSlice<>(new SliceImpl<>(List.of(show)), "next");
        when(showListQueryRepository.findAllBySearch(param, 10, "popular")).thenReturn(result);

        GetShowsUseCase.Output output = useCase.execute(new GetShowsUseCase.Input(param, 10, ShowSort.from("popular")));

        assertThat(output.shows().getContent()).containsExactly(show);
        assertThat(output.nextCursor()).isEqualTo("next");
        verify(showListQueryRepository).findAllBySearch(param, 10, "popular");
    }

    @Test
    void 공연이_없으면_빈_슬라이스와_null_커서를_반환한다() {
        ShowParam param = new ShowParam(null, null, null, null);
        CursorSlice<ShowListItemView> result = new CursorSlice<>(new SliceImpl<>(List.of()), null);
        when(showListQueryRepository.findAllBySearch(param, 10, "popular")).thenReturn(result);

        GetShowsUseCase.Output output = useCase.execute(new GetShowsUseCase.Input(param, 10, ShowSort.from("popular")));

        assertThat(output.shows().getContent()).isEmpty();
        assertThat(output.nextCursor()).isNull();
        verify(showListQueryRepository).findAllBySearch(param, 10, "popular");
    }
}
