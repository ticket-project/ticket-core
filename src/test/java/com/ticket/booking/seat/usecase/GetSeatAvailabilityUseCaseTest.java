package com.ticket.booking.seat.usecase;

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
import com.ticket.booking.seat.domain.PerformanceSeat;
import com.ticket.booking.seat.domain.PerformanceSeatState;
import com.ticket.booking.selection.domain.SeatSelectionService;
import com.ticket.show.api.PerformanceSaleCatalogApi;
import com.ticket.show.api.PerformanceSaleSnapshot;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
class GetSeatAvailabilityUseCaseTest {
    @Mock
    private SeatAvailabilitySnapshotReader seatAvailabilitySnapshotReader;

    @Mock
    private PerformanceSaleCatalogApi performanceSaleCatalogApi;

    @Mock
    private HoldManager holdManager;

    @Mock
    private SeatSelectionService seatSelectionService;

    @InjectMocks
    private GetSeatAvailabilityUseCase useCase;

    @Test
    void DB와_redis_점유좌석을_합쳐_잔여석을_계산한다() {
        List<PerformanceSeat> stateRows =
                List.of(PerformanceSeatFixture.seat(501L, 1L, 31L, PerformanceSeatState.AVAILABLE));
        PerformanceSaleSnapshot saleSnapshot = saleSnapshotWithGrade(31L, "VIP", "VIP석", 1);

        when(seatAvailabilitySnapshotReader.read(10L)).thenReturn(stateRows);
        when(performanceSaleCatalogApi.getSaleSnapshot(10L, Set.of())).thenReturn(saleSnapshot);
        // seat 1은 selecting, seat 2는 holding으로 점유돼 있다. 둘 다 잔여석에서 빠진다.
        when(seatSelectionService.getSelectingSeatIds(10L)).thenReturn(Set.of(1L));
        when(holdManager.getHoldingSeatIds(10L)).thenReturn(Set.of(2L));
        GetSeatAvailabilityUseCase.Output output = useCase.execute(new GetSeatAvailabilityUseCase.Input(10L));
        assertThat(output.grades())
                .containsExactly(
                        new GetSeatAvailabilityUseCase.GradeAvailability(31L, "VIP", "VIP석", BigDecimal.TEN, 1, 0L));
    }

    /** 옛 {@code SeatAvailabilityCalculatorTest}에서 옮겨 온다 — 집계가 use case의 private method가 됐다. */
    @Test
    void RESERVED_좌석은_잔여석에서_제외한다() {
        final List<PerformanceSeat> stateRows = List.of(
                PerformanceSeatFixture.seat(501L, 1L, 31L, PerformanceSeatState.AVAILABLE),
                PerformanceSeatFixture.seat(502L, 2L, 31L, PerformanceSeatState.RESERVED));

        when(seatAvailabilitySnapshotReader.read(10L)).thenReturn(stateRows);
        when(performanceSaleCatalogApi.getSaleSnapshot(10L, Set.of()))
                .thenReturn(saleSnapshotWithGrade(31L, "VIP", "VIP석", 1));
        when(seatSelectionService.getSelectingSeatIds(10L)).thenReturn(Set.of());
        when(holdManager.getHoldingSeatIds(10L)).thenReturn(Set.of());

        final GetSeatAvailabilityUseCase.Output output = useCase.execute(new GetSeatAvailabilityUseCase.Input(10L));

        assertThat(output.grades())
                .extracting(GetSeatAvailabilityUseCase.GradeAvailability::availableSeats)
                .containsExactly(1L);
    }

    /** 좌석이 있으면 잔여석이 0이어도 등급은 결과에 남는다 — "매진"을 보여줘야 하기 때문이다. */
    @Test
    void 좌석이_있으면_잔여석이_0이어도_grade는_결과에_포함된다() {
        final List<PerformanceSeat> stateRows =
                List.of(PerformanceSeatFixture.seat(501L, 1L, 31L, PerformanceSeatState.RESERVED));

        when(seatAvailabilitySnapshotReader.read(10L)).thenReturn(stateRows);
        when(performanceSaleCatalogApi.getSaleSnapshot(10L, Set.of()))
                .thenReturn(saleSnapshotWithGrade(31L, "VIP", "VIP석", 1));
        when(seatSelectionService.getSelectingSeatIds(10L)).thenReturn(Set.of());
        when(holdManager.getHoldingSeatIds(10L)).thenReturn(Set.of());

        final GetSeatAvailabilityUseCase.Output output = useCase.execute(new GetSeatAvailabilityUseCase.Input(10L));

        assertThat(output.grades())
                .extracting(GetSeatAvailabilityUseCase.GradeAvailability::availableSeats)
                .containsExactly(0L);
    }

    @Test
    void 이름이_같아도_performanceGradeId가_다르면_따로_집계한다() {
        List<PerformanceSeat> stateRows = List.of(
                PerformanceSeatFixture.seat(501L, 1L, 31L, PerformanceSeatState.AVAILABLE),
                PerformanceSeatFixture.seat(502L, 2L, 32L, PerformanceSeatState.AVAILABLE));
        PerformanceSaleSnapshot saleSnapshot = new PerformanceSaleSnapshot(
                10L,
                100L,
                "show-title",
                1L,
                "venue-name",
                null,
                Map.of(),
                Map.of(
                        31L,
                        new PerformanceSaleSnapshot.GradeInfo(31L, "VIP", "같은이름", 1, BigDecimal.TEN),
                        32L,
                        new PerformanceSaleSnapshot.GradeInfo(32L, "R", "같은이름", 2, BigDecimal.valueOf(5))));

        when(seatAvailabilitySnapshotReader.read(10L)).thenReturn(stateRows);
        when(performanceSaleCatalogApi.getSaleSnapshot(10L, Set.of())).thenReturn(saleSnapshot);
        when(seatSelectionService.getSelectingSeatIds(10L)).thenReturn(Set.of());
        when(holdManager.getHoldingSeatIds(10L)).thenReturn(Set.of());
        GetSeatAvailabilityUseCase.Output output = useCase.execute(new GetSeatAvailabilityUseCase.Input(10L));
        assertThat(output.grades()).hasSize(2);
        assertThat(output.grades())
                .extracting(GetSeatAvailabilityUseCase.GradeAvailability::performanceGradeId)
                .containsExactlyInAnyOrder(31L, 32L);
    }

    /** 회차 존재 확인과 좌석 상태 조회를 한 짧은 트랜잭션에서 끝내고, Redis·show 호출은 그 밖에서 한다. */
    @Test
    void DB_읽기는_짧은_트랜잭션에서_끝내고_use_case는_트랜잭션을_열지_않는다() throws NoSuchMethodException {
        assertThat(GetSeatAvailabilityUseCase.class.isAnnotationPresent(
                        org.springframework.transaction.annotation.Transactional.class))
                .isFalse();
        assertThat(GetSeatAvailabilityUseCase.class
                        .getDeclaredMethod("execute", GetSeatAvailabilityUseCase.Input.class)
                        .isAnnotationPresent(org.springframework.transaction.annotation.Transactional.class))
                .isFalse();
        assertThat(SeatAvailabilitySnapshotReader.class
                        .getDeclaredMethod("read", Long.class)
                        .getAnnotation(org.springframework.transaction.annotation.Transactional.class)
                        .readOnly())
                .isTrue();
    }

    @Test
    void 회차의_좌석_상태가_없으면_show_판매_snapshot을_조회하지_않는다() {
        when(seatAvailabilitySnapshotReader.read(10L)).thenReturn(List.of());
        useCase.execute(new GetSeatAvailabilityUseCase.Input(10L));
        verify(performanceSaleCatalogApi, org.mockito.Mockito.never())
                .getSaleSnapshot(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anySet());
    }

    private PerformanceSaleSnapshot saleSnapshotWithGrade(
            final long performanceGradeId, final String gradeCode, final String gradeName, final int sortOrder) {
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
                                performanceGradeId, gradeCode, gradeName, sortOrder, BigDecimal.TEN)));
    }
}
