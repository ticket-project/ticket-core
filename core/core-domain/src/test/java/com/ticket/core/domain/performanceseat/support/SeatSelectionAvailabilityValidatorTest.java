package com.ticket.core.domain.performanceseat.support;

import com.ticket.core.domain.hold.command.HoldManager;
import com.ticket.core.domain.performanceseat.model.PerformanceSeatState;
import com.ticket.core.domain.performanceseat.query.SeatSelectionAvailabilityQueryRepository;
import com.ticket.core.domain.performanceseat.query.model.SeatSelectionAvailabilityView;
import com.ticket.support.error.CoreException;
import com.ticket.support.error.ErrorType;
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
    private SeatSelectionAvailabilityQueryRepository queryRepository;

    @InjectMocks
    private SeatSelectionAvailabilityValidator validator;

    @Test
    void 선택_가능한_좌석이면_통과한다() {
        when(queryRepository.findSelectableSeat(10L, 20L)).thenReturn(Optional.of(available()));
        when(holdManager.isHeld(10L, 20L)).thenReturn(false);

        assertThatCode(() -> validator.validate(10L, 20L)).doesNotThrowAnyException();
    }

    @Test
    void 회차에_없는_좌석이면_실패한다() {
        when(queryRepository.findSelectableSeat(10L, 20L)).thenReturn(Optional.empty());

        assertError(ErrorType.SEAT_MISMATCH_IN_PERFORMANCE);

        verifyNoInteractions(holdManager);
    }

    @Test
    void 이미_예약된_좌석이면_실패한다() {
        when(queryRepository.findSelectableSeat(10L, 20L))
                .thenReturn(Optional.of(new SeatSelectionAvailabilityView(30L, PerformanceSeatState.RESERVED)));

        assertError(ErrorType.NOT_EXIST_AVAILABLE_SEAT);

        verifyNoInteractions(holdManager);
    }

    @Test
    void 이미_hold된_좌석이면_실패한다() {
        when(queryRepository.findSelectableSeat(10L, 20L)).thenReturn(Optional.of(available()));
        when(holdManager.isHeld(10L, 20L)).thenReturn(true);

        assertError(ErrorType.SEAT_ALREADY_HOLD);
    }

    private void assertError(final ErrorType errorType) {
        assertThatThrownBy(() -> validator.validate(10L, 20L))
                .isInstanceOf(CoreException.class)
                .satisfies(exception -> assertThat(((CoreException) exception).getErrorType()).isEqualTo(errorType));
    }

    private SeatSelectionAvailabilityView available() {
        return new SeatSelectionAvailabilityView(30L, PerformanceSeatState.AVAILABLE);
    }
}
