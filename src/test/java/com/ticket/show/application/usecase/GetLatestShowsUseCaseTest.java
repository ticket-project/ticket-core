package com.ticket.show.application.usecase;

import com.ticket.show.application.LatestShowRow;
import com.ticket.show.application.ShowSummaryView;

import com.ticket.show.application.port.ShowListQueryPort;

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
class GetLatestShowsUseCaseTest {

    @Mock
    private ShowListQueryPort showListQueryPort;

    @Mock
    private VenueLookup venueLookup;

    @InjectMocks
    private GetLatestShowsUseCase useCase;

    @Test
    void 최신_공연은_최대_10개를_조회한다() {
        LocalDate startDate = LocalDate.of(2026, 3, 27);
        LocalDate endDate = LocalDate.of(2026, 3, 28);
        LocalDateTime createdAt = LocalDateTime.of(2026, 3, 1, 12, 0);
        List<LatestShowRow> rows = List.of(
                new LatestShowRow(1L, "concert", "image", startDate, endDate, 7L, createdAt)
        );
        when(showListQueryPort.findLatestShows("CONCERT", GetLatestShowsUseCase.LATEST_SHOWS_MAX_COUNT)).thenReturn(rows);
        when(venueLookup.getSummaries(Set.of(7L))).thenReturn(Map.of(7L, new VenueSummary(
                7L, "venue", "주소", null, null, null, null, null,
                new VenueSummary.SeatMapLayout(0, 0, 0.0)
        )));

        GetLatestShowsUseCase.Output output = useCase.execute(new GetLatestShowsUseCase.Input("CONCERT"));

        assertThat(output.shows()).containsExactly(
                new ShowSummaryView(1L, "concert", "image", startDate, endDate, "venue", createdAt)
        );
        verify(showListQueryPort).findLatestShows("CONCERT", GetLatestShowsUseCase.LATEST_SHOWS_MAX_COUNT);
    }

    @Test
    void 최신_공연이_없으면_빈_목록을_반환한다() {
        when(showListQueryPort.findLatestShows("CONCERT", GetLatestShowsUseCase.LATEST_SHOWS_MAX_COUNT))
                .thenReturn(List.of());

        GetLatestShowsUseCase.Output output = useCase.execute(new GetLatestShowsUseCase.Input("CONCERT"));

        assertThat(output.shows()).isEmpty();
        verify(showListQueryPort).findLatestShows("CONCERT", GetLatestShowsUseCase.LATEST_SHOWS_MAX_COUNT);
    }
}
