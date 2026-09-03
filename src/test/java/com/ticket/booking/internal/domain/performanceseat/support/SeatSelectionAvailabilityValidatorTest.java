package com.ticket.booking.internal.domain.performanceseat.support;

import com.ticket.booking.internal.domain.performanceseat.repository.PerformanceSeatRepository;
import com.ticket.booking.internal.domain.hold.command.HoldManager;
import com.ticket.booking.internal.domain.performanceseat.model.PerformanceSeatState;
import com.ticket.booking.internal.domain.performanceseat.query.model.SeatSelectionAvailabilitySnapshot;
import com.ticket.booking.internal.exception.BookingException;
import com.ticket.booking.internal.exception.NoAvailableSeatException;
import com.ticket.booking.internal.exception.SeatAlreadyHoldException;
import com.ticket.booking.internal.exception.SeatMismatchInPerformanceException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
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
    void 선택_가능한_좌석이면_통과한다() {
        when(performanceSeatRepository.findSelectableSeat(10L, 20L)).thenReturn(Optional.of(available()));
        when(holdManager.isHeld(10L, 20L)).thenReturn(false);

        assertThatCode(() -> validator.validate(10L, 20L)).doesNotThrowAnyException();
    }

    @Test
    void 회차에_없는_좌석이면_실패한다() {
        when(performanceSeatRepository.findSelectableSeat(10L, 20L)).thenReturn(Optional.empty());

        assertError(SeatMismatchInPerformanceException.class);

        verifyNoInteractions(holdManager);
    }

    @Test
    void 이미_예약된_좌석이면_실패한다() {
        when(performanceSeatRepository.findSelectableSeat(10L, 20L))
                .thenReturn(Optional.of(new SeatSelectionAvailabilitySnapshot(30L, PerformanceSeatState.RESERVED)));

        assertError(NoAvailableSeatException.class);

        verifyNoInteractions(holdManager);
    }

    @Test
    void 이미_hold된_좌석이면_실패한다() {
        when(performanceSeatRepository.findSelectableSeat(10L, 20L)).thenReturn(Optional.of(available()));
        when(holdManager.isHeld(10L, 20L)).thenReturn(true);

        assertError(SeatAlreadyHoldException.class);
    }

    private void assertError(final Class<? extends BookingException> expected) {
        assertThatThrownBy(() -> validator.validate(10L, 20L))
                .isInstanceOf(expected);
    }

    private SeatSelectionAvailabilitySnapshot available() {
        return new SeatSelectionAvailabilitySnapshot(30L, PerformanceSeatState.AVAILABLE);
    }
}
