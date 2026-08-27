package com.ticket.core.app.show.query;

import com.ticket.core.app.show.query.model.ShowSummaryView;
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
class GetLatestShowsUseCaseTest {

    @Mock
    private ShowListReadRepository showListReadRepository;

    @InjectMocks
    private GetLatestShowsUseCase useCase;

    @Test
    void 최신_공연은_최대_10개를_조회한다() {
        LocalDate startDate = LocalDate.of(2026, 3, 27);
        LocalDate endDate = LocalDate.of(2026, 3, 28);
        LocalDateTime createdAt = LocalDateTime.of(2026, 3, 1, 12, 0);
        List<ShowSummaryView> responses = List.of(
                new ShowSummaryView(1L, "concert", "image", startDate, endDate, "venue", createdAt)
        );
        when(showListReadRepository.findLatestShows("CONCERT", GetLatestShowsUseCase.LATEST_SHOWS_MAX_COUNT)).thenReturn(responses);

        GetLatestShowsUseCase.Output output = useCase.execute(new GetLatestShowsUseCase.Input("CONCERT"));

        assertThat(output.shows()).containsExactlyElementsOf(responses);
        verify(showListReadRepository).findLatestShows("CONCERT", GetLatestShowsUseCase.LATEST_SHOWS_MAX_COUNT);
    }

    @Test
    void 최신_공연이_없으면_빈_목록을_반환한다() {
        when(showListReadRepository.findLatestShows("CONCERT", GetLatestShowsUseCase.LATEST_SHOWS_MAX_COUNT))
                .thenReturn(List.of());

        GetLatestShowsUseCase.Output output = useCase.execute(new GetLatestShowsUseCase.Input("CONCERT"));

        assertThat(output.shows()).isEmpty();
        verify(showListReadRepository).findLatestShows("CONCERT", GetLatestShowsUseCase.LATEST_SHOWS_MAX_COUNT);
    }
}
