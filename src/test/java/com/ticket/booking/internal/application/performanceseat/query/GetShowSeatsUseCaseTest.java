package com.ticket.booking.internal.application.performanceseat.query;

import com.ticket.booking.internal.application.performanceseat.query.model.SeatInfoView;
import com.ticket.catalog.ShowLookup;
import com.ticket.catalog.ShowSeatMapEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
class GetShowSeatsUseCaseTest {

    @Mock
    private ShowLookup showLookup;

    @InjectMocks
    private GetShowSeatsUseCase useCase;

    @Test
    void 공연_좌석_정보를_조회한다() {
        List<ShowSeatMapEntry> seats = List.of(
                new ShowSeatMapEntry(1L, 1, "A", "10", "7", 10.0, 20.0, "VIP", "VIP", BigDecimal.TEN, 1)
        );
        when(showLookup.getSeatMap(100L)).thenReturn(seats);

        GetShowSeatsUseCase.Output output = useCase.execute(new GetShowSeatsUseCase.Input(100L));

        assertThat(output.seats()).containsExactly(
                new SeatInfoView(1L, 1, "A", "10", "7", 10.0, 20.0, "VIP", "VIP", BigDecimal.TEN)
        );
        verify(showLookup).getSeatMap(100L);
    }

    @Test
    void 공연_좌석이_없으면_빈_목록을_반환한다() {
        when(showLookup.getSeatMap(100L)).thenReturn(List.of());

        GetShowSeatsUseCase.Output output = useCase.execute(new GetShowSeatsUseCase.Input(100L));

        assertThat(output.seats()).isEmpty();
        verify(showLookup).getSeatMap(100L);
    }
}
