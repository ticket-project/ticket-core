package com.ticket.show.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ticket.show.application.port.ShowListQueryPort;
import com.ticket.show.application.query.ShowSearchCriteria;
import com.ticket.venue.api.VenueLookupApi;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
class CountSearchShowsUseCaseTest {
    @Mock private ShowListQueryPort showListQueryPort;
    @Mock private VenueLookupApi venueLookup;
    @InjectMocks private CountSearchShowsUseCase useCase;

    @Test
    void 검색_개수를_응답으로_감싼다() {
        ShowSearchCriteria request =
                new ShowSearchCriteria("뮤지컬", null, null, null, null, null, null);
        when(showListQueryPort.countSearchShows(request, null)).thenReturn(42L);

        CountSearchShowsUseCase.Output output =
                useCase.execute(new CountSearchShowsUseCase.Input(request));

        assertThat(output.count()).isEqualTo(42L);
        verify(showListQueryPort).countSearchShows(request, null);
    }

    @Test
    void 검색결과가_없으면_0건을_반환한다() {
        ShowSearchCriteria request =
                new ShowSearchCriteria("없는공연", null, null, null, null, null, null);
        when(showListQueryPort.countSearchShows(request, null)).thenReturn(0L);

        CountSearchShowsUseCase.Output output =
                useCase.execute(new CountSearchShowsUseCase.Input(request));

        assertThat(output.count()).isZero();
        verify(showListQueryPort).countSearchShows(request, null);
    }
}
