package com.ticket.booking.hold.domain;

import com.ticket.booking.support.domain.RequestedSeatIds;
import com.ticket.booking.domain.PerformanceSeat;
import com.ticket.booking.domain.PerformanceSeatRepository;
import com.ticket.booking.domain.PerformanceSeatState;
import com.ticket.booking.exception.NoAvailableSeatException;
import com.ticket.booking.exception.SeatMismatchInPerformanceException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SuppressWarnings("NonAsciiCharacters")
@ExtendWith(MockitoExtension.class)
class HoldSeatAvailabilityValidatorTest {

    @Mock
    private PerformanceSeatRepository performanceSeatRepository;

    @InjectMocks
    private HoldSeatAvailabilityValidator validator;

    @Test
    void validate는_requestedSeatIds를_직접_받는다() throws NoSuchMethodException {
        Method method = HoldSeatAvailabilityValidator.class.getDeclaredMethod(
                "validate",
                Long.class,
                RequestedSeatIds.class
        );

        assertThat(method.getParameterTypes()[1]).isEqualTo(RequestedSeatIds.class);
    }

    @Test
    void 좌석개수가_일치하지_않으면_seatMismatch예외를_던진다() {
        PerformanceSeat availableSeat = mock(PerformanceSeat.class);
        RequestedSeatIds seatIds = RequestedSeatIds.from(List.of(10L, 20L));
        when(performanceSeatRepository.findAllByPerformanceIdAndSeatIdIn(1L, List.of(10L, 20L)))
                .thenReturn(List.of(availableSeat));

        assertThatThrownBy(() -> validator.validate(1L, seatIds))
                .isInstanceOf(SeatMismatchInPerformanceException.class);
    }

    @Test
    void 사용불가_좌석이_포함되면_notExistAvailableSeat예외를_던진다() {
        PerformanceSeat availableSeat = createPerformanceSeat(PerformanceSeatState.AVAILABLE);
        PerformanceSeat reservedSeat = createPerformanceSeat(PerformanceSeatState.RESERVED);
        RequestedSeatIds seatIds = RequestedSeatIds.from(List.of(10L, 20L));
        when(performanceSeatRepository.findAllByPerformanceIdAndSeatIdIn(1L, List.of(10L, 20L)))
                .thenReturn(List.of(availableSeat, reservedSeat));

        assertThatThrownBy(() -> validator.validate(1L, seatIds))
                .isInstanceOf(NoAvailableSeatException.class);
    }

    @Test
    void 모두_예매가능_좌석이면_그대로_반환한다() {
        RequestedSeatIds seatIds = RequestedSeatIds.from(List.of(10L, 20L));
        List<PerformanceSeat> seats = List.of(
                createPerformanceSeat(PerformanceSeatState.AVAILABLE),
                createPerformanceSeat(PerformanceSeatState.AVAILABLE)
        );
        when(performanceSeatRepository.findAllByPerformanceIdAndSeatIdIn(1L, List.of(10L, 20L))).thenReturn(seats);

        List<PerformanceSeat> result = validator.validate(1L, seatIds);

        assertThat(result).containsExactlyElementsOf(seats);
    }

    private PerformanceSeat createPerformanceSeat(final PerformanceSeatState state) {
        PerformanceSeat performanceSeat = mock(PerformanceSeat.class);
        when(performanceSeat.getState()).thenReturn(state);
        return performanceSeat;
    }
}
