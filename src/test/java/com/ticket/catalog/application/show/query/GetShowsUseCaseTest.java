package com.ticket.catalog.application.show.query;

import com.ticket.catalog.domain.show.SaleType;
import com.ticket.catalog.application.show.query.model.ShowListItemView;
import com.ticket.catalog.application.show.query.model.ShowParam;
import com.ticket.catalog.application.show.query.model.ShowCursor;
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
class GetShowsUseCaseTest {

    private static final ShowCursor NEXT_POSITION =
            new ShowCursor(ShowSort.POPULAR, "DESC", "10", 1L);

    @Mock
    private ShowListReadRepository showListReadRepository;

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
        CursorPage<ShowListItemView, ShowCursor> result = new CursorPage<>(List.of(show), true, NEXT_POSITION);
        when(showListReadRepository.findAllBySearch(param, 10, ShowSort.POPULAR)).thenReturn(result);

        GetShowsUseCase.Output output = useCase.execute(new GetShowsUseCase.Input(param, 10, ShowSort.from("popular")));

        assertThat(output.items()).containsExactly(show);
        assertThat(output.nextPosition()).isEqualTo(NEXT_POSITION);
        assertThat(output.hasNext()).isTrue();
        verify(showListReadRepository).findAllBySearch(param, 10, ShowSort.POPULAR);
    }

    @Test
    void 공연이_없으면_빈_슬라이스와_null_커서를_반환한다() {
        ShowParam param = new ShowParam(null, null, null, null);
        CursorPage<ShowListItemView, ShowCursor> result = new CursorPage<>(List.of(), false, null);
        when(showListReadRepository.findAllBySearch(param, 10, ShowSort.POPULAR)).thenReturn(result);

        GetShowsUseCase.Output output = useCase.execute(new GetShowsUseCase.Input(param, 10, ShowSort.from("popular")));

        assertThat(output.items()).isEmpty();
        assertThat(output.nextPosition()).isNull();
        assertThat(output.hasNext()).isFalse();
        verify(showListReadRepository).findAllBySearch(param, 10, ShowSort.POPULAR);
    }
}
