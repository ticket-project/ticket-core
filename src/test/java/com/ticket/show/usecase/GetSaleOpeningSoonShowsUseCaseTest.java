package com.ticket.show.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ticket.show.persistence.ShowQueryRepository;
import com.ticket.show.query.SaleOpeningSoonSummaryRow;
import com.ticket.show.usecase.view.SaleOpeningSoonSummaryView;
import com.ticket.venue.api.VenueLookupApi;
import com.ticket.venue.api.VenueSummary;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
class GetSaleOpeningSoonShowsUseCaseTest {
    @Mock private ShowQueryRepository showQueryRepository;
    @Mock private VenueLookupApi venueLookup;
    @InjectMocks private GetSaleOpeningSoonShowsUseCase useCase;

    @Test
    void 판매시작임박_공연_목록을_반환한다() {
        LocalDateTime saleStartDate = LocalDateTime.of(2026, 3, 27, 12, 0);
        List<SaleOpeningSoonSummaryRow> rows =
                List.of(new SaleOpeningSoonSummaryRow(1L, "concert", "image", 7L, saleStartDate));
        when(showQueryRepository.findSaleOpeningSoonSummaries("CONCERT", 5)).thenReturn(rows);
        when(venueLookup.getSummaries(Set.of(7L)))
                .thenReturn(
                        Map.of(
                                7L,
                                new VenueSummary(
                                        7L,
                                        "venue",
                                        "주소",
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        new VenueSummary.SeatMapLayout(0, 0, 0.0))));

        GetSaleOpeningSoonShowsUseCase.Output output =
                useCase.execute(new GetSaleOpeningSoonShowsUseCase.Input("CONCERT", 5));

        assertThat(output.shows())
                .containsExactly(
                        new SaleOpeningSoonSummaryView(
                                1L, "concert", "image", "venue", saleStartDate));
        verify(showQueryRepository).findSaleOpeningSoonSummaries("CONCERT", 5);
    }

    @Test
    void 판매시작임박_공연이_없으면_빈_목록을_반환한다() {
        when(showQueryRepository.findSaleOpeningSoonSummaries("CONCERT", 5)).thenReturn(List.of());

        GetSaleOpeningSoonShowsUseCase.Output output =
                useCase.execute(new GetSaleOpeningSoonShowsUseCase.Input("CONCERT", 5));

        assertThat(output.shows()).isEmpty();
        verify(showQueryRepository).findSaleOpeningSoonSummaries("CONCERT", 5);
    }
}
