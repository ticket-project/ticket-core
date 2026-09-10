package com.ticket.show.catalog.application.usecase;

import com.ticket.show.catalog.application.ShowListItemRow;
import com.ticket.show.catalog.application.ShowSort;

import com.ticket.show.catalog.application.port.ShowListQueryPort;

import com.ticket.show.catalog.domain.SaleType;
import com.ticket.show.catalog.application.ShowParam;
import com.ticket.show.catalog.application.ShowCursor;
import com.ticket.shared.CursorPage;
import com.ticket.venue.VenueLookup;
import com.ticket.venue.VenueSummary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
class GetShowsUseCaseTest {

    private static final ShowCursor NEXT_POSITION =
            new ShowCursor(ShowSort.POPULAR, "DESC", "10", 1L);

    @Mock
    private ShowListQueryPort showListQueryPort;

    @Mock
    private VenueLookup venueLookup;

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

        ShowListItemRow row = new ShowListItemRow(
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
                7L
        );
        CursorPage<ShowListItemRow, ShowCursor> result = new CursorPage<>(List.of(row), true, NEXT_POSITION);
        when(showListQueryPort.findAllBySearch(param, 10, ShowSort.POPULAR)).thenReturn(result);
        when(venueLookup.getSummaries(Set.of(7L))).thenReturn(Map.of(7L, new VenueSummary(
                7L, "venue", "주소", null, null, null, null, null,
                new VenueSummary.SeatMapLayout(0, 0, 0.0)
        )));

        GetShowsUseCase.Output output = useCase.execute(new GetShowsUseCase.Input(param, 10, ShowSort.from("popular")));

        assertThat(output.items()).hasSize(1);
        assertThat(output.items().getFirst().title()).isEqualTo("concert");
        assertThat(output.items().getFirst().venue()).isEqualTo("venue");
        assertThat(output.nextPosition()).isEqualTo(NEXT_POSITION);
        assertThat(output.hasNext()).isTrue();
        verify(showListQueryPort).findAllBySearch(param, 10, ShowSort.POPULAR);
    }

    @Test
    void 공연이_없으면_빈_슬라이스와_null_커서를_반환한다() {
        ShowParam param = new ShowParam(null, null, null, null);
        CursorPage<ShowListItemRow, ShowCursor> result = new CursorPage<>(List.of(), false, null);
        when(showListQueryPort.findAllBySearch(param, 10, ShowSort.POPULAR)).thenReturn(result);

        GetShowsUseCase.Output output = useCase.execute(new GetShowsUseCase.Input(param, 10, ShowSort.from("popular")));

        assertThat(output.items()).isEmpty();
        assertThat(output.nextPosition()).isNull();
        assertThat(output.hasNext()).isFalse();
        verify(showListQueryPort).findAllBySearch(param, 10, ShowSort.POPULAR);
    }
}
