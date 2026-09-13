package com.ticket.booking.seat.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ticket.booking.hold.domain.HoldManager;
import com.ticket.booking.seat.application.SeatAvailabilityCalculator;
import com.ticket.booking.seat.application.SeatAvailabilitySnapshotReader;
import com.ticket.booking.seat.application.port.SeatAvailabilityQueryPort.PerformanceSeatStateRow;
import com.ticket.booking.seat.domain.PerformanceSeatState;
import com.ticket.booking.selection.domain.SeatSelectionService;
import com.ticket.show.PerformanceSaleCatalog;
import com.ticket.show.PerformanceSaleSnapshot;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
class GetSeatAvailabilityUseCaseTest {
    @Mock private SeatAvailabilitySnapshotReader seatAvailabilitySnapshotReader;
    @Mock private PerformanceSaleCatalog performanceSaleCatalog;
    @Mock private HoldManager holdManager;
    @Mock private SeatSelectionService seatSelectionService;
    @Mock private SeatAvailabilityCalculator seatAvailabilityCalculator;
    @InjectMocks private GetSeatAvailabilityUseCase useCase;

    @Test
    void DB와_redis_점유좌석을_합쳐_잔여석을_계산한다() {
        // given
        List<PerformanceSeatStateRow> stateRows =
                List.of(new PerformanceSeatStateRow(1L, PerformanceSeatState.AVAILABLE, 31L));
        PerformanceSaleSnapshot saleSnapshot = saleSnapshotWithGrade(31L, "VIP", "VIP석", 1);

        when(seatAvailabilitySnapshotReader.read(10L)).thenReturn(stateRows);
        when(performanceSaleCatalog.getSaleSnapshot(10L, Set.of())).thenReturn(saleSnapshot);
        when(seatSelectionService.getSelectingSeatIds(10L)).thenReturn(Set.of(1L));
        when(holdManager.getHoldingSeatIds(10L)).thenReturn(Set.of(2L));
        when(seatAvailabilityCalculator.calculate(stateRows, Set.of(1L, 2L)))
                .thenReturn(Map.of(31L, 0L));
        // when
        GetSeatAvailabilityUseCase.Output output =
                useCase.execute(new GetSeatAvailabilityUseCase.Input(10L));
        // then
        assertThat(output.grades())
                .containsExactly(
                        new GetSeatAvailabilityUseCase.GradeAvailability(
                                31L, "VIP", "VIP석", BigDecimal.TEN, 1, 0L));
        verify(seatAvailabilityCalculator).calculate(stateRows, Set.of(1L, 2L));
    }

    @Test
    void 이름이_같아도_performanceGradeId가_다르면_따로_집계한다() {
        // given
        List<PerformanceSeatStateRow> stateRows =
                List.of(
                        new PerformanceSeatStateRow(1L, PerformanceSeatState.AVAILABLE, 31L),
                        new PerformanceSeatStateRow(2L, PerformanceSeatState.AVAILABLE, 32L));
        PerformanceSaleSnapshot saleSnapshot =
                new PerformanceSaleSnapshot(
                        10L,
                        100L,
                        "show-title",
                        1L,
                        "venue-name",
                        null,
                        Map.of(),
                        Map.of(
                                31L,
                                new PerformanceSaleSnapshot.GradeInfo(
                                        31L, "VIP", "같은이름", 1, BigDecimal.TEN),
                                32L,
                                new PerformanceSaleSnapshot.GradeInfo(
                                        32L, "R", "같은이름", 2, BigDecimal.valueOf(5))));

        when(seatAvailabilitySnapshotReader.read(10L)).thenReturn(stateRows);
        when(performanceSaleCatalog.getSaleSnapshot(10L, Set.of())).thenReturn(saleSnapshot);
        when(seatSelectionService.getSelectingSeatIds(10L)).thenReturn(Set.of());
        when(holdManager.getHoldingSeatIds(10L)).thenReturn(Set.of());
        when(seatAvailabilityCalculator.calculate(stateRows, Set.of()))
                .thenReturn(Map.of(31L, 1L, 32L, 1L));
        // when
        GetSeatAvailabilityUseCase.Output output =
                useCase.execute(new GetSeatAvailabilityUseCase.Input(10L));
        // then
        assertThat(output.grades()).hasSize(2);
        assertThat(output.grades())
                .extracting(GetSeatAvailabilityUseCase.GradeAvailability::performanceGradeId)
                .containsExactlyInAnyOrder(31L, 32L);
    }

    /** 회차 존재 확인과 좌석 상태 조회를 한 짧은 트랜잭션에서 끝내고, Redis·show 호출은 그 밖에서 한다. */
    @Test
    void DB_읽기는_짧은_트랜잭션에서_끝내고_use_case는_트랜잭션을_열지_않는다() throws NoSuchMethodException {
        assertThat(
                        GetSeatAvailabilityUseCase.class.isAnnotationPresent(
                                org.springframework.transaction.annotation.Transactional.class))
                .isFalse();
        assertThat(
                        GetSeatAvailabilityUseCase.class
                                .getDeclaredMethod(
                                        "execute", GetSeatAvailabilityUseCase.Input.class)
                                .isAnnotationPresent(
                                        org.springframework.transaction.annotation.Transactional
                                                .class))
                .isFalse();
        assertThat(
                        SeatAvailabilitySnapshotReader.class
                                .getDeclaredMethod("read", Long.class)
                                .getAnnotation(
                                        org.springframework.transaction.annotation.Transactional
                                                .class)
                                .readOnly())
                .isTrue();
    }

    @Test
    void 회차의_좌석_상태가_없으면_show_판매_snapshot을_조회하지_않는다() {
        // given
        when(seatAvailabilitySnapshotReader.read(10L)).thenReturn(List.of());
        // when
        useCase.execute(new GetSeatAvailabilityUseCase.Input(10L));
        // then
        verify(performanceSaleCatalog, org.mockito.Mockito.never())
                .getSaleSnapshot(
                        org.mockito.ArgumentMatchers.anyLong(),
                        org.mockito.ArgumentMatchers.anySet());
    }

    private PerformanceSaleSnapshot saleSnapshotWithGrade(
            final long performanceGradeId,
            final String gradeCode,
            final String gradeName,
            final int sortOrder) {
        return new PerformanceSaleSnapshot(
                10L,
                100L,
                "show-title",
                1L,
                "venue-name",
                null,
                Map.of(),
                Map.of(
                        performanceGradeId,
                        new PerformanceSaleSnapshot.GradeInfo(
                                performanceGradeId,
                                gradeCode,
                                gradeName,
                                sortOrder,
                                BigDecimal.TEN)));
    }
}
