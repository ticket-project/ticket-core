package com.ticket.booking.application;

import com.ticket.booking.domain.HoldManager;
import com.ticket.booking.application.SeatAvailabilityReadRepository.PerformanceSeatStateRow;
import com.ticket.booking.domain.SeatSelectionService;
import com.ticket.booking.domain.BookingEntryPolicy;
import com.ticket.booking.domain.HoldPolicy;
import com.ticket.booking.domain.OrderAcceptanceWindow;
import com.ticket.booking.domain.PerformanceSalesPolicy;
import com.ticket.booking.domain.PerformanceSalesPolicyRepository;
import com.ticket.booking.domain.PerformanceSeatState;
import com.ticket.show.PerformanceSaleCatalog;
import com.ticket.show.PerformanceSaleSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
class GetSeatAvailabilityUseCaseTest {

    @Mock
    private PerformanceSalesPolicyRepository performanceSalesPolicyRepository;
    @Mock
    private PerformanceSaleCatalog performanceSaleCatalog;
    @Mock
    private SeatAvailabilityReadRepository seatAvailabilityReadRepository;
    @Mock
    private HoldManager holdManager;
    @Mock
    private SeatSelectionService seatSelectionService;
    @Mock
    private SeatAvailabilityCalculator seatAvailabilityCalculator;

    @InjectMocks
    private GetSeatAvailabilityUseCase useCase;

    @Test
    void DB와_redis_점유좌석을_합쳐_잔여석을_계산한다() {
        //given
        List<PerformanceSeatStateRow> stateRows =
                List.of(new PerformanceSeatStateRow(1L, PerformanceSeatState.AVAILABLE, 31L));
        PerformanceSaleSnapshot saleSnapshot = saleSnapshotWithGrade(31L, "VIP", "VIP석", 1);

        when(performanceSalesPolicyRepository.findById(10L)).thenReturn(Optional.of(policy()));
        when(seatAvailabilityReadRepository.findSeatStates(10L)).thenReturn(stateRows);
        when(performanceSaleCatalog.getSaleSnapshot(10L, Set.of())).thenReturn(saleSnapshot);
        when(seatSelectionService.getSelectingSeatIds(10L)).thenReturn(Set.of(1L));
        when(holdManager.getHoldingSeatIds(10L)).thenReturn(Set.of(2L));
        when(seatAvailabilityCalculator.calculate(stateRows, Set.of(1L, 2L))).thenReturn(Map.of(31L, 0L));

        //when
        GetSeatAvailabilityUseCase.Output output = useCase.execute(new GetSeatAvailabilityUseCase.Input(10L));

        //then
        assertThat(output.grades()).containsExactly(
                new GetSeatAvailabilityUseCase.GradeAvailability(31L, "VIP", "VIP석", BigDecimal.TEN, 1, 0L)
        );
        verify(seatAvailabilityCalculator).calculate(stateRows, Set.of(1L, 2L));
    }

    @Test
    void 이름이_같아도_performanceGradeId가_다르면_따로_집계한다() {
        //given
        List<PerformanceSeatStateRow> stateRows = List.of(
                new PerformanceSeatStateRow(1L, PerformanceSeatState.AVAILABLE, 31L),
                new PerformanceSeatStateRow(2L, PerformanceSeatState.AVAILABLE, 32L)
        );
        PerformanceSaleSnapshot saleSnapshot = new PerformanceSaleSnapshot(
                10L, 100L, "show-title", 1L, "venue-name", null,
                Map.of(),
                Map.of(
                        31L, new PerformanceSaleSnapshot.GradeInfo(31L, "VIP", "같은이름", 1, BigDecimal.TEN),
                        32L, new PerformanceSaleSnapshot.GradeInfo(32L, "R", "같은이름", 2, BigDecimal.valueOf(5))
                )
        );

        when(performanceSalesPolicyRepository.findById(10L)).thenReturn(Optional.of(policy()));
        when(seatAvailabilityReadRepository.findSeatStates(10L)).thenReturn(stateRows);
        when(performanceSaleCatalog.getSaleSnapshot(10L, Set.of())).thenReturn(saleSnapshot);
        when(seatSelectionService.getSelectingSeatIds(10L)).thenReturn(Set.of());
        when(holdManager.getHoldingSeatIds(10L)).thenReturn(Set.of());
        when(seatAvailabilityCalculator.calculate(stateRows, Set.of())).thenReturn(Map.of(31L, 1L, 32L, 1L));

        //when
        GetSeatAvailabilityUseCase.Output output = useCase.execute(new GetSeatAvailabilityUseCase.Input(10L));

        //then
        assertThat(output.grades()).hasSize(2);
        assertThat(output.grades()).extracting(GetSeatAvailabilityUseCase.GradeAvailability::performanceGradeId)
                .containsExactlyInAnyOrder(31L, 32L);
    }

    @Test
    void 회차의_좌석_상태가_없으면_show_판매_snapshot을_조회하지_않는다() {
        //given
        when(performanceSalesPolicyRepository.findById(10L)).thenReturn(Optional.of(policy()));
        when(seatAvailabilityReadRepository.findSeatStates(10L)).thenReturn(List.of());

        //when
        useCase.execute(new GetSeatAvailabilityUseCase.Input(10L));

        //then
        verify(performanceSaleCatalog, org.mockito.Mockito.never())
                .getSaleSnapshot(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anySet());
    }

    private PerformanceSalesPolicy policy() {
        final LocalDateTime now = LocalDateTime.of(2026, 3, 15, 10, 0);
        return new PerformanceSalesPolicy(
                10L,
                new OrderAcceptanceWindow(now.minusHours(1), now.plusHours(3)),
                new HoldPolicy(4, Duration.ofSeconds(300)),
                BookingEntryPolicy.none()
        );
    }

    private PerformanceSaleSnapshot saleSnapshotWithGrade(
            final long performanceGradeId,
            final String gradeCode,
            final String gradeName,
            final int sortOrder
    ) {
        return new PerformanceSaleSnapshot(
                10L, 100L, "show-title", 1L, "venue-name", null,
                Map.of(),
                Map.of(performanceGradeId, new PerformanceSaleSnapshot.GradeInfo(
                        performanceGradeId, gradeCode, gradeName, sortOrder, BigDecimal.TEN))
        );
    }
}
