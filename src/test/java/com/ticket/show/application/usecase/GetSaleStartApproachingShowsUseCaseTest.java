package com.ticket.show.application.usecase;

import com.ticket.show.application.SaleOpeningSoonSummaryRow;
import com.ticket.show.application.ShowOpeningSoonSummaryView;

import com.ticket.show.application.port.ShowListQueryPort;

import com.ticket.venue.VenueLookup;
import com.ticket.venue.VenueSummary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
class GetSaleStartApproachingShowsUseCaseTest {

    @Mock
    private ShowListQueryPort showListQueryPort;

    @Mock
    private VenueLookup venueLookup;

    @InjectMocks
    private GetSaleStartApproachingShowsUseCase useCase;

    @Test
    void 판매시작임박_공연_목록을_반환한다() {
        LocalDateTime saleStartDate = LocalDateTime.of(2026, 3, 27, 12, 0);
        List<SaleOpeningSoonSummaryRow> rows = List.of(
                new SaleOpeningSoonSummaryRow(1L, "concert", "image", 7L, saleStartDate)
        );
        when(showListQueryPort.findShowsSaleOpeningSoon("CONCERT", 5)).thenReturn(rows);
        when(venueLookup.getSummaries(Set.of(7L))).thenReturn(Map.of(7L, new VenueSummary(
                7L, "venue", "주소", null, null, null, null, null,
                new VenueSummary.SeatMapLayout(0, 0, 0.0)
        )));

        GetSaleStartApproachingShowsUseCase.Output output = useCase.execute(new GetSaleStartApproachingShowsUseCase.Input("CONCERT", 5));

        assertThat(output.shows()).containsExactly(
                new ShowOpeningSoonSummaryView(1L, "concert", "image", "venue", saleStartDate)
        );
        verify(showListQueryPort).findShowsSaleOpeningSoon("CONCERT", 5);
    }

    @Test
    void 판매시작임박_공연이_없으면_빈_목록을_반환한다() {
        when(showListQueryPort.findShowsSaleOpeningSoon("CONCERT", 5)).thenReturn(List.of());

        GetSaleStartApproachingShowsUseCase.Output output =
                useCase.execute(new GetSaleStartApproachingShowsUseCase.Input("CONCERT", 5));

        assertThat(output.shows()).isEmpty();
        verify(showListQueryPort).findShowsSaleOpeningSoon("CONCERT", 5);
    }
}
