package com.ticket.booking.application.usecase;

import com.ticket.booking.application.port.PerformanceSeatMapQueryPort;

import com.ticket.booking.application.port.PerformanceSeatMapQueryPort.PerformanceSeatMapRow;
import com.ticket.show.PerformanceVenueLayout;
import com.ticket.show.PerformanceVenueLayoutCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
class GetPerformanceSeatMapUseCaseTest {

    @Mock
    private PerformanceVenueLayoutCatalog performanceVenueLayoutCatalog;
    @Mock
    private PerformanceSeatMapQueryPort performanceSeatMapQueryPort;

    @InjectMocks
    private GetPerformanceSeatMapUseCase useCase;

    @Test
    void 편성된_좌석만_venue_배치와_등급을_조합해_반환한다() {
        //given
        PerformanceVenueLayout layout = new PerformanceVenueLayout(
                10L, 3L, "예술의전당", 500, 356, 4.8,
                Map.of(
                        101L, new PerformanceVenueLayout.SeatLayout(101L, 1, "가", "A", "1", 129.0, 101.0),
                        102L, new PerformanceVenueLayout.SeatLayout(102L, 1, "가", "A", "2", 140.0, 101.0)
                ),
                Map.of(31L, new PerformanceVenueLayout.GradeLayout(31L, "VIP", "VIP석", 1))
        );
        // seat 102는 이 회차에 판매 편성(PerformanceSeat)되지 않아 응답에서 제외돼야 한다.
        List<PerformanceSeatMapRow> rows = List.of(
                new PerformanceSeatMapRow(501L, 101L, 31L, BigDecimal.valueOf(170000))
        );

        when(performanceVenueLayoutCatalog.getVenueLayout(10L)).thenReturn(layout);
        when(performanceSeatMapQueryPort.findAllByPerformanceId(10L)).thenReturn(rows);

        //when
        GetPerformanceSeatMapUseCase.Output output = useCase.execute(new GetPerformanceSeatMapUseCase.Input(10L));

        //then
        assertThat(output.venue().venueId()).isEqualTo(3L);
        assertThat(output.seats()).hasSize(1);
        GetPerformanceSeatMapUseCase.SeatMapEntry entry = output.seats().get(0);
        assertThat(entry.performanceSeatId()).isEqualTo(501L);
        assertThat(entry.seatId()).isEqualTo(101L);
        assertThat(entry.performanceGradeId()).isEqualTo(31L);
        assertThat(entry.gradeCode()).isEqualTo("VIP");
        assertThat(entry.price()).isEqualByComparingTo(BigDecimal.valueOf(170000));
        assertThat(output.seats()).extracting(GetPerformanceSeatMapUseCase.SeatMapEntry::seatId)
                .doesNotContain(102L);
    }

    @Test
    void 같은_venue라도_회차마다_다른_좌석과_가격을_반환한다() {
        //given
        PerformanceVenueLayout layoutA = new PerformanceVenueLayout(
                10L, 3L, "예술의전당", 500, 356, 4.8,
                Map.of(101L, new PerformanceVenueLayout.SeatLayout(101L, 1, "가", "A", "1", 129.0, 101.0)),
                Map.of(31L, new PerformanceVenueLayout.GradeLayout(31L, "VIP", "VIP석", 1))
        );
        PerformanceVenueLayout layoutB = new PerformanceVenueLayout(
                20L, 3L, "예술의전당", 500, 356, 4.8,
                Map.of(101L, new PerformanceVenueLayout.SeatLayout(101L, 1, "가", "A", "1", 129.0, 101.0)),
                Map.of(32L, new PerformanceVenueLayout.GradeLayout(32L, "R", "R석", 1))
        );
        when(performanceVenueLayoutCatalog.getVenueLayout(10L)).thenReturn(layoutA);
        when(performanceVenueLayoutCatalog.getVenueLayout(20L)).thenReturn(layoutB);
        when(performanceSeatMapQueryPort.findAllByPerformanceId(10L))
                .thenReturn(List.of(new PerformanceSeatMapRow(501L, 101L, 31L, BigDecimal.valueOf(170000))));
        when(performanceSeatMapQueryPort.findAllByPerformanceId(20L))
                .thenReturn(List.of(new PerformanceSeatMapRow(601L, 101L, 32L, BigDecimal.valueOf(90000))));

        //when
        GetPerformanceSeatMapUseCase.Output outputA = useCase.execute(new GetPerformanceSeatMapUseCase.Input(10L));
        GetPerformanceSeatMapUseCase.Output outputB = useCase.execute(new GetPerformanceSeatMapUseCase.Input(20L));

        //then
        assertThat(outputA.seats().get(0).gradeCode()).isEqualTo("VIP");
        assertThat(outputA.seats().get(0).price()).isEqualByComparingTo(BigDecimal.valueOf(170000));
        assertThat(outputB.seats().get(0).gradeCode()).isEqualTo("R");
        assertThat(outputB.seats().get(0).price()).isEqualByComparingTo(BigDecimal.valueOf(90000));
    }

    @Test
    void 좌석_수와_무관하게_show와_booking_조회는_각각_한_번씩만_한다() {
        //given
        Map<Long, PerformanceVenueLayout.SeatLayout> seatLayouts = new java.util.HashMap<>();
        List<PerformanceSeatMapRow> rows = new java.util.ArrayList<>();
        for (long seatId = 1; seatId <= 50; seatId++) {
            seatLayouts.put(seatId, new PerformanceVenueLayout.SeatLayout(seatId, 1, "가", "A", String.valueOf(seatId), seatId, seatId));
            rows.add(new PerformanceSeatMapRow(seatId + 1000, seatId, 31L, BigDecimal.valueOf(10000)));
        }
        PerformanceVenueLayout layout = new PerformanceVenueLayout(
                10L, 3L, "venue", 500, 356, 4.8,
                seatLayouts,
                Map.of(31L, new PerformanceVenueLayout.GradeLayout(31L, "VIP", "VIP석", 1))
        );
        when(performanceVenueLayoutCatalog.getVenueLayout(10L)).thenReturn(layout);
        when(performanceSeatMapQueryPort.findAllByPerformanceId(10L)).thenReturn(rows);

        //when
        GetPerformanceSeatMapUseCase.Output output = useCase.execute(new GetPerformanceSeatMapUseCase.Input(10L));

        //then
        assertThat(output.seats()).hasSize(50);
        verify(performanceVenueLayoutCatalog, times(1)).getVenueLayout(10L);
        verify(performanceSeatMapQueryPort, times(1)).findAllByPerformanceId(10L);
    }
}
