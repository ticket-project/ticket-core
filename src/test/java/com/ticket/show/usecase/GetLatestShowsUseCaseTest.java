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
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ticket.show.domain.show.Show;
import com.ticket.show.persistence.ShowQuerydslRepository;
import com.ticket.venue.api.VenueLookupApi;
import com.ticket.venue.api.VenueSnapshot;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
class GetLatestShowsUseCaseTest {
    @Mock
    private ShowQuerydslRepository showQuerydslRepository;

    @Mock
    private VenueLookupApi venueLookup;

    @Spy
    private ShowCardImagePathConverter showCardImagePathConverter = new ShowCardImagePathConverter();

    @InjectMocks
    private GetLatestShowsUseCase useCase;

    @Test
    void 최신_공연은_최대_10개를_조회한다() {
        LocalDate startDate = LocalDate.of(2026, 3, 27);
        LocalDate endDate = LocalDate.of(2026, 3, 28);
        LocalDateTime createdAt = LocalDateTime.of(2026, 3, 1, 12, 0);
        List<Show> rows = List.of(ShowFixture.show(1L, "concert", 7L, startDate, endDate, null, 0L, createdAt));
        when(showQuerydslRepository.findLatestShows("CONCERT", GetLatestShowsUseCase.LATEST_SHOWS_MAX_COUNT))
                .thenReturn(rows);
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

        GetLatestShowsUseCase.Output output = useCase.execute(new GetLatestShowsUseCase.Input("CONCERT"));

        assertThat(output.shows())
                .containsExactly(
                        new GetLatestShowsUseCase.Item(1L, "concert", "image", startDate, endDate, "venue", createdAt));
        verify(showQuerydslRepository).findLatestShows("CONCERT", GetLatestShowsUseCase.LATEST_SHOWS_MAX_COUNT);
    }

    @Test
    void 최신_공연이_없으면_빈_목록을_반환한다() {
        when(showQuerydslRepository.findLatestShows("CONCERT", GetLatestShowsUseCase.LATEST_SHOWS_MAX_COUNT))
                .thenReturn(List.of());

        GetLatestShowsUseCase.Output output = useCase.execute(new GetLatestShowsUseCase.Input("CONCERT"));

        assertThat(output.shows()).isEmpty();
        verify(showQuerydslRepository).findLatestShows("CONCERT", GetLatestShowsUseCase.LATEST_SHOWS_MAX_COUNT);
    }
}
