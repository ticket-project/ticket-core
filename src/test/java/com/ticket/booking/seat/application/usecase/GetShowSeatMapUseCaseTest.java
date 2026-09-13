package com.ticket.booking.seat.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ticket.show.ShowPerformanceLookup;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
class GetShowSeatMapUseCaseTest {
    @Mock private ShowPerformanceLookup showPerformanceLookup;
    @Mock private GetPerformanceSeatMapUseCase getPerformanceSeatMapUseCase;
    @InjectMocks private GetShowSeatMapUseCase useCase;

    @Test
    void 공연의_첫_회차_좌석을_기존_프론트_형식으로_변환한다() {
        when(showPerformanceLookup.findRepresentativePerformanceId(1L))
                .thenReturn(Optional.of(10L));
        when(getPerformanceSeatMapUseCase.execute(new GetPerformanceSeatMapUseCase.Input(10L)))
                .thenReturn(
                        new GetPerformanceSeatMapUseCase.Output(
                                new GetPerformanceSeatMapUseCase.VenueView(
                                        2L, "공연장", 500, 356, 4.8),
                                List.of(
                                        new GetPerformanceSeatMapUseCase.SeatMapEntry(
                                                1001L,
                                                101L,
                                                1,
                                                "가",
                                                "A",
                                                "1",
                                                10.0,
                                                20.0,
                                                201L,
                                                "VIP",
                                                "VIP석",
                                                BigDecimal.valueOf(170000)))));

        GetShowSeatMapUseCase.Output output = useCase.execute(new GetShowSeatMapUseCase.Input(1L));

        assertThat(output.seats())
                .containsExactly(
                        new GetShowSeatMapUseCase.SeatMapEntry(
                                101L,
                                1,
                                "가",
                                "A",
                                "1",
                                10.0,
                                20.0,
                                "VIP",
                                "VIP석",
                                BigDecimal.valueOf(170000)));
    }
}
