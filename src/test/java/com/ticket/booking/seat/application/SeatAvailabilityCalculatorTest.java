package com.ticket.booking.seat.application;

import com.ticket.booking.seat.application.port.SeatAvailabilityQueryPort;

import com.ticket.booking.seat.application.port.SeatAvailabilityQueryPort.PerformanceSeatStateRow;
import com.ticket.booking.seat.domain.PerformanceSeatState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("NonAsciiCharacters")
class SeatAvailabilityCalculatorTest {

    private final SeatAvailabilityCalculator calculator = new SeatAvailabilityCalculator();

    @Test
    void RESERVED_좌석은_잔여석에서_제외한다() {
        List<PerformanceSeatStateRow> rows = List.of(
                new PerformanceSeatStateRow(1L, PerformanceSeatState.AVAILABLE, 31L),
                new PerformanceSeatStateRow(2L, PerformanceSeatState.RESERVED, 31L)
        );

        Map<Long, Long> result = calculator.calculate(rows, Set.of());

        assertThat(result).containsEntry(31L, 1L);
    }

    @Test
    void redis에_selecting_또는_holding으로_점유된_available_좌석은_잔여석에서_제외한다() {
        List<PerformanceSeatStateRow> rows = List.of(
                new PerformanceSeatStateRow(1L, PerformanceSeatState.AVAILABLE, 31L),
                new PerformanceSeatStateRow(2L, PerformanceSeatState.AVAILABLE, 31L),
                new PerformanceSeatStateRow(3L, PerformanceSeatState.AVAILABLE, 31L)
        );

        // seat 1은 selecting, seat 2는 holding으로 점유된 상태를 흉내낸다 — 병합 이후 함수는
        // 둘을 구분하지 않고 같은 occupied 집합으로 받는다.
        Map<Long, Long> result = calculator.calculate(rows, Set.of(1L, 2L));

        assertThat(result).containsEntry(31L, 1L);
    }

    @Test
    void 좌석이_있으면_잔여석이_0이어도_grade는_결과에_포함된다() {
        List<PerformanceSeatStateRow> rows = List.of(
                new PerformanceSeatStateRow(1L, PerformanceSeatState.RESERVED, 31L)
        );

        Map<Long, Long> result = calculator.calculate(rows, Set.of());

        assertThat(result).containsEntry(31L, 0L);
    }
}
