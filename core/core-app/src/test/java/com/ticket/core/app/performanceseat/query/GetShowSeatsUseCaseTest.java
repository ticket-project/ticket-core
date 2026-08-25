package com.ticket.core.app.performanceseat.query;

import com.ticket.core.app.performanceseat.query.model.SeatInfoView;
import com.ticket.core.domain.show.model.Show;
import com.ticket.core.domain.show.query.ShowFinder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
class GetShowSeatsUseCaseTest {

    @Mock
    private ShowFinder showFinder;

    @Mock
    private SeatMapQueryRepository seatMapQueryRepository;

    @InjectMocks
    private GetShowSeatsUseCase useCase;

    @Test
    void 공연_좌석_정보를_조회한다() {
        Show show = mock(Show.class);
        List<SeatInfoView> seats = List.of(
                new SeatInfoView(1L, 1, "A", "10", "7", 10.0, 20.0, "VIP", "VIP", BigDecimal.TEN)
        );
        when(showFinder.findById(100L)).thenReturn(show);
        when(show.getId()).thenReturn(100L);
        when(seatMapQueryRepository.findShowSeats(100L)).thenReturn(seats);

        GetShowSeatsUseCase.Output output = useCase.execute(new GetShowSeatsUseCase.Input(100L));

        assertThat(output.seats()).containsExactlyElementsOf(seats);
        verify(seatMapQueryRepository).findShowSeats(100L);
    }

    @Test
    void 공연_좌석이_없으면_빈_목록을_반환한다() {
        Show show = mock(Show.class);
        when(showFinder.findById(100L)).thenReturn(show);
        when(show.getId()).thenReturn(100L);
        when(seatMapQueryRepository.findShowSeats(100L)).thenReturn(List.of());

        GetShowSeatsUseCase.Output output = useCase.execute(new GetShowSeatsUseCase.Input(100L));

        assertThat(output.seats()).isEmpty();
        verify(seatMapQueryRepository).findShowSeats(100L);
    }
}
