package com.ticket.booking.selection.domain;

import com.ticket.booking.seat.domain.PerformanceSeatRepository;
import com.ticket.booking.hold.domain.HoldManager;
import com.ticket.booking.seat.domain.PerformanceSeatState;
import com.ticket.booking.selection.domain.SeatSelectionAvailabilitySnapshot;
import com.ticket.booking.support.exception.BookingException;
import com.ticket.booking.support.exception.NoAvailableSeatException;
import com.ticket.booking.support.exception.SeatAlreadyHoldException;
import com.ticket.booking.support.exception.SeatMismatchInPerformanceException;
import org.assertj.core.api.AbstractThrowableAssert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
class SeatSelectionAvailabilityValidatorTest {

    @Mock
    private HoldManager holdManager;

    @Mock
    private PerformanceSeatRepository performanceSeatRepository;

    @InjectMocks
    private SeatSelectionAvailabilityValidator validator;

    @Test
    void 선택_가능한_좌석이면_performanceSeatId를_반환한다() {
        when(performanceSeatRepository.findSelectableSeat(10L, 20L)).thenReturn(Optional.of(available()));
        when(holdManager.isHeld(10L, 20L)).thenReturn(false);

        assertThat(validator.validate(10L, 20L)).isEqualTo(30L);
    }

    @Test
    void 회차에_없는_좌석이면_실패한다() {
        when(performanceSeatRepository.findSelectableSeat(10L, 20L)).thenReturn(Optional.empty());

        assertError(SeatMismatchInPerformanceException.class)
                .hasFieldOrPropertyWithValue("performanceId", 10L);

        verifyNoInteractions(holdManager);
    }

    @Test
    void 이미_예약된_좌석이면_실패한다() {
        when(performanceSeatRepository.findSelectableSeat(10L, 20L))
                .thenReturn(Optional.of(new SeatSelectionAvailabilitySnapshot(30L, PerformanceSeatState.RESERVED)));

        assertError(NoAvailableSeatException.class)
                .hasFieldOrPropertyWithValue("performanceId", 10L);

        verifyNoInteractions(holdManager);
    }

    @Test
    void 이미_hold된_좌석이면_실패한다() {
        when(performanceSeatRepository.findSelectableSeat(10L, 20L)).thenReturn(Optional.of(available()));
        when(holdManager.isHeld(10L, 20L)).thenReturn(true);

        assertError(SeatAlreadyHoldException.class)
                .hasFieldOrPropertyWithValue("performanceId", 10L)
                .hasFieldOrPropertyWithValue("seatId", 20L);
    }

    private AbstractThrowableAssert<?, ? extends Throwable> assertError(
            final Class<? extends BookingException> expected
    ) {
        return assertThatThrownBy(() -> validator.validate(10L, 20L))
                .isInstanceOf(expected);
    }

    private SeatSelectionAvailabilitySnapshot available() {
        return new SeatSelectionAvailabilitySnapshot(30L, PerformanceSeatState.AVAILABLE);
    }
}
