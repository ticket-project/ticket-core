package com.ticket.booking.internal.application.performanceseat.query;

import com.ticket.booking.internal.domain.hold.command.HoldManager;
import com.ticket.booking.internal.application.performanceseat.query.SeatAvailabilityReadRepository.PerformanceSeatStateRow;
import com.ticket.booking.internal.domain.performanceseat.command.SeatSelectionService;
import com.ticket.booking.internal.domain.performanceseat.model.PerformanceSeatState;
import com.ticket.booking.internal.application.performanceseat.query.model.AvailableSeatRow;
import com.ticket.catalog.BookingPolicyLookup;
import com.ticket.catalog.BookingPolicySnapshot;
import com.ticket.catalog.ShowLookup;
import com.ticket.catalog.ShowSeatMapEntry;
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
    private ShowLookup showLookup;
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
                List.of(new PerformanceSeatStateRow(1L, PerformanceSeatState.AVAILABLE));
        List<ShowSeatMapEntry> seatMap = List.of(
                new ShowSeatMapEntry(1L, 1, "A", "10", "7", 10.0, 20.0, "VIP", "VIP", BigDecimal.TEN, 1)
        );
        List<AvailableSeatRow> rows =
                List.of(new AvailableSeatRow(1L, PerformanceSeatState.AVAILABLE, "VIP", 1));
        List<GetSeatAvailabilityUseCase.GradeAvailability> response =
                List.of(new GetSeatAvailabilityUseCase.GradeAvailability("VIP", 1, 0L));

        when(bookingPolicyLookup.getBookingPolicy(10L)).thenReturn(policy(100L));
        when(seatAvailabilityReadRepository.findSeatStates(10L)).thenReturn(stateRows);
        when(showLookup.getSeatMap(100L)).thenReturn(seatMap);
        when(seatSelectionService.getSelectingSeatIds(10L)).thenReturn(Set.of(1L));
        when(holdManager.getHoldingSeatIds(10L)).thenReturn(Set.of(2L));
        when(seatAvailabilityCalculator.calculate(rows, Set.of(1L, 2L))).thenReturn(response);

        //when
        GetSeatAvailabilityUseCase.Output output = useCase.execute(new GetSeatAvailabilityUseCase.Input(10L));

        //then
        assertThat(output.grades()).isEqualTo(response);
        verify(seatAvailabilityCalculator).calculate(rows, Set.of(1L, 2L));
    }

    @Test
    void 회차의_좌석_상태가_없으면_catalog_seat_map을_조회하지_않는다() {
        //given
        when(bookingPolicyLookup.getBookingPolicy(10L)).thenReturn(policy(100L));
        when(seatAvailabilityReadRepository.findSeatStates(10L)).thenReturn(List.of());
        when(seatSelectionService.getSelectingSeatIds(10L)).thenReturn(Set.of());
        when(holdManager.getHoldingSeatIds(10L)).thenReturn(Set.of());
        when(seatAvailabilityCalculator.calculate(List.of(), Set.of())).thenReturn(List.of());

        //when
        useCase.execute(new GetSeatAvailabilityUseCase.Input(10L));

        //then
        verify(showLookup, org.mockito.Mockito.never()).getSeatMap(org.mockito.ArgumentMatchers.anyLong());
    }

    private BookingPolicySnapshot policy(final long showId) {
        return new BookingPolicySnapshot(
                10L, showId, true,
                null, null, 4, 300, null, null, null, false
        );
    }
}
