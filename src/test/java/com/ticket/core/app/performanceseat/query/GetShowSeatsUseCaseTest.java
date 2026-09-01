package com.ticket.core.app.performanceseat.query;

import com.ticket.core.app.performanceseat.query.model.SeatInfoView;
import com.ticket.core.domain.show.model.Show;
import com.ticket.core.domain.show.repository.ShowRepository;
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
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
class GetShowSeatsUseCaseTest {

    @Mock
    private ShowRepository showRepository;

    @Mock
    private SeatMapReadRepository seatMapReadRepository;

    @InjectMocks
    private GetShowSeatsUseCase useCase;

    @Test
    void 공연_좌석_정보를_조회한다() {
        Show show = mock(Show.class);
        List<SeatInfoView> seats = List.of(
                new SeatInfoView(1L, 1, "A", "10", "7", 10.0, 20.0, "VIP", "VIP", BigDecimal.TEN)
        );
        when(showRepository.findById(100L)).thenReturn(Optional.of(show));
        when(show.getId()).thenReturn(100L);
        when(seatMapReadRepository.findShowSeats(100L)).thenReturn(seats);

        GetShowSeatsUseCase.Output output = useCase.execute(new GetShowSeatsUseCase.Input(100L));

        assertThat(output.seats()).containsExactlyElementsOf(seats);
        verify(seatMapReadRepository).findShowSeats(100L);
    }

    @Test
    void 공연_좌석이_없으면_빈_목록을_반환한다() {
        Show show = mock(Show.class);
        when(showRepository.findById(100L)).thenReturn(Optional.of(show));
        when(show.getId()).thenReturn(100L);
        when(seatMapReadRepository.findShowSeats(100L)).thenReturn(List.of());

        GetShowSeatsUseCase.Output output = useCase.execute(new GetShowSeatsUseCase.Input(100L));

        assertThat(output.seats()).isEmpty();
        verify(seatMapReadRepository).findShowSeats(100L);
    }
}
