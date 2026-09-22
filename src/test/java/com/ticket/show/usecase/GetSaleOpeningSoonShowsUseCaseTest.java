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
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ticket.show.domain.show.Show;
import com.ticket.show.domain.show.ShowCardImagePathConverter;
import com.ticket.show.persistence.ShowQuerydslRepository;
import com.ticket.venue.api.VenueLookupApi;
import com.ticket.venue.api.VenueSnapshot;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
class GetSaleOpeningSoonShowsUseCaseTest {
    @Mock
    private ShowQuerydslRepository showQuerydslRepository;

    @Mock
    private VenueLookupApi venueLookup;

    @Spy
    private ShowCardImagePathConverter showCardImagePathConverter = new ShowCardImagePathConverter();

    @InjectMocks
    private GetSaleOpeningSoonShowsUseCase useCase;

    @Test
    void 판매시작임박_공연_목록을_반환한다() {
        LocalDateTime saleStartDate = LocalDateTime.of(2026, 3, 27, 12, 0);
        List<Show> rows = List.of(ShowFixture.show(1L, "concert", 7L, null, null, saleStartDate, 0L, saleStartDate));
        when(showQuerydslRepository.findSaleOpeningSoonSummaries("CONCERT", 5)).thenReturn(rows);
        when(venueLookup.getSummaries(Set.of(7L)))
                .thenReturn(Map.of(
                        7L,
                        new VenueSnapshot(
                                7L,
                                "venue",
                                "주소",
                                null,
                                null,
                                null,
                                null,
                                null,
                                new VenueSnapshot.SeatMapLayout(0, 0, 0.0))));

        GetSaleOpeningSoonShowsUseCase.Output output =
                useCase.execute(new GetSaleOpeningSoonShowsUseCase.Input("CONCERT", 5));

        assertThat(output.shows())
                .containsExactly(
                        new GetSaleOpeningSoonShowsUseCase.Item(1L, "concert", "image", "venue", saleStartDate));
        verify(showQuerydslRepository).findSaleOpeningSoonSummaries("CONCERT", 5);
    }

    @Test
    void 판매시작임박_공연이_없으면_빈_목록을_반환한다() {
        when(showQuerydslRepository.findSaleOpeningSoonSummaries("CONCERT", 5)).thenReturn(List.of());

        GetSaleOpeningSoonShowsUseCase.Output output =
                useCase.execute(new GetSaleOpeningSoonShowsUseCase.Input("CONCERT", 5));

        assertThat(output.shows()).isEmpty();
        verify(showQuerydslRepository).findSaleOpeningSoonSummaries("CONCERT", 5);
    }
}
