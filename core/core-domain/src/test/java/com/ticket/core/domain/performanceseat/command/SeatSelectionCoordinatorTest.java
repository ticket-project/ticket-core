package com.ticket.core.domain.performanceseat.command;

import com.ticket.core.domain.hold.command.HoldManager;
import com.ticket.core.support.exception.CoreException;
import com.ticket.core.support.exception.ErrorType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
class SeatSelectionCoordinatorTest {

    @Mock
    private HoldManager holdManager;

    @Mock
    private SeatSelectionService seatSelectionService;

    @InjectMocks
    private SeatSelectionCoordinator coordinator;

    @Test
    void 락_내부에서_홀드를_다시_확인하고_좌석을_선점한다() {
        when(holdManager.isHeld(10L, 20L)).thenReturn(false);

        coordinator.select(10L, 20L, 1L);

        verify(seatSelectionService).select(10L, 20L, 1L);
    }

    @Test
    void DB검증_후_홀드된_좌석이면_선점을_중단한다() {
        when(holdManager.isHeld(10L, 20L)).thenReturn(true);

        assertThatThrownBy(() -> coordinator.select(10L, 20L, 1L))
                .isInstanceOf(CoreException.class)
                .satisfies(exception -> assertThat(((CoreException) exception).getErrorType())
                        .isEqualTo(ErrorType.SEAT_ALREADY_HOLD));

        verifyNoInteractions(seatSelectionService);
    }
}
