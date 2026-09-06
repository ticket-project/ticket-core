package com.ticket.booking.application.performanceseat.query;

import com.ticket.booking.domain.hold.command.HoldManager;
import com.ticket.booking.application.performanceseat.query.SeatAvailabilityReadRepository.PerformanceSeatStateRow;
import com.ticket.booking.domain.performanceseat.command.SeatSelectionService;
import com.ticket.booking.domain.performanceseat.model.PerformanceSeatState;
import com.ticket.catalog.BookingPolicyLookup;
import com.ticket.catalog.BookingPolicySnapshot;
import com.ticket.catalog.PerformanceSaleCatalog;
import com.ticket.catalog.PerformanceSaleSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
class GetSeatAvailabilityUseCaseTest {

    @Mock
    private BookingPolicyLookup bookingPolicyLookup;
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

        when(bookingPolicyLookup.getBookingPolicy(10L)).thenReturn(policy(100L));
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

        when(bookingPolicyLookup.getBookingPolicy(10L)).thenReturn(policy(100L));
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
    void 회차의_좌석_상태가_없으면_catalog_판매_snapshot을_조회하지_않는다() {
        //given
        when(bookingPolicyLookup.getBookingPolicy(10L)).thenReturn(policy(100L));
        when(seatAvailabilityReadRepository.findSeatStates(10L)).thenReturn(List.of());

        //when
        useCase.execute(new GetSeatAvailabilityUseCase.Input(10L));

        //then
        verify(performanceSaleCatalog, org.mockito.Mockito.never())
                .getSaleSnapshot(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anySet());
    }

    private BookingPolicySnapshot policy(final long showId) {
        return new BookingPolicySnapshot(
                10L, showId, true,
                null, null, 4, 300, null, null, null, false
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
