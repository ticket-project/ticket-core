package com.ticket.show.application;

import com.ticket.show.application.ShowOpeningSoonSummaryView;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
class GetSaleStartApproachingShowsUseCaseTest {

    @Mock
    private ShowListReadRepository showListReadRepository;

    @InjectMocks
    private GetSaleStartApproachingShowsUseCase useCase;

    @Test
    void 판매시작임박_공연_목록을_반환한다() {
        LocalDateTime saleStartDate = LocalDateTime.of(2026, 3, 27, 12, 0);
        List<ShowOpeningSoonSummaryView> shows = List.of(
                new ShowOpeningSoonSummaryView(1L, "concert", "image", "venue", saleStartDate)
        );
        when(showListReadRepository.findShowsSaleOpeningSoon("CONCERT", 5)).thenReturn(shows);

        GetSaleStartApproachingShowsUseCase.Output output = useCase.execute(new GetSaleStartApproachingShowsUseCase.Input("CONCERT", 5));

        assertThat(output.shows()).containsExactlyElementsOf(shows);
        verify(showListReadRepository).findShowsSaleOpeningSoon("CONCERT", 5);
    }

    @Test
    void 판매시작임박_공연이_없으면_빈_목록을_반환한다() {
        when(showListReadRepository.findShowsSaleOpeningSoon("CONCERT", 5)).thenReturn(List.of());

        GetSaleStartApproachingShowsUseCase.Output output =
                useCase.execute(new GetSaleStartApproachingShowsUseCase.Input("CONCERT", 5));

        assertThat(output.shows()).isEmpty();
        verify(showListReadRepository).findShowsSaleOpeningSoon("CONCERT", 5);
    }
}
