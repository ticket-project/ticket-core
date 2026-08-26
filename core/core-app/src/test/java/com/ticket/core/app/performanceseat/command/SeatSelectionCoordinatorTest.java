package com.ticket.core.app.performanceseat.command;

import com.ticket.support.error.CoreException;
import com.ticket.core.domain.error.DomainErrorType;
import com.ticket.core.domain.performanceseat.command.SeatSelectionService;
import com.ticket.core.domain.hold.command.HoldManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
class SeatSelectionCoordinatorTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-04T01:00:00Z"),
            ZoneId.of("Asia/Seoul")
    );
    private static final LocalDateTime NOW = LocalDateTime.now(CLOCK);

    @Mock
    private HoldManager holdManager;

    @Mock
    private SeatSelectionService seatSelectionService;

    private SeatSelectionCoordinator coordinator;

    @BeforeEach
    void setUp() {
        coordinator = new SeatSelectionCoordinator(holdManager, seatSelectionService, CLOCK);
    }

    @Test
    void 락_내부에서_홀드를_다시_확인하고_좌석을_선점한다() {
        when(holdManager.isHeld(10L, 20L)).thenReturn(false);

        coordinator.select(10L, 20L, 1L, NOW.plusMinutes(1));

        verify(seatSelectionService).select(10L, 20L, 1L);
    }

    @Test
    void DB검증_후_홀드된_좌석이면_선점을_중단한다() {
        when(holdManager.isHeld(10L, 20L)).thenReturn(true);

        assertThatThrownBy(() -> coordinator.select(10L, 20L, 1L, NOW.plusMinutes(1)))
                .isInstanceOf(CoreException.class)
                .satisfies(exception -> assertThat(((CoreException) exception).getErrorType())
                        .isEqualTo(DomainErrorType.SEAT_ALREADY_HOLD));

        verifyNoInteractions(seatSelectionService);
    }

    @Test
    void 락_획득_시점에_예매가_마감됐으면_선점을_중단한다() {
        assertThatThrownBy(() -> coordinator.select(10L, 20L, 1L, NOW.minusNanos(1)))
                .isInstanceOf(CoreException.class)
                .satisfies(exception -> assertThat(((CoreException) exception).getErrorType())
                        .isEqualTo(DomainErrorType.PERFORMANCE_IS_PAST));

        verifyNoInteractions(holdManager, seatSelectionService);
    }
}
