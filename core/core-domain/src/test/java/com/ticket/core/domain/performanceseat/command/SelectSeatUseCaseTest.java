package com.ticket.core.domain.performanceseat.command;

import com.ticket.core.domain.performanceseat.support.SeatStatusEventPublisher;
import com.ticket.core.domain.performanceseat.support.SeatSelectionAvailabilityValidator;
import com.ticket.core.domain.performanceseat.support.SeatStatusMessage.SeatAction;
import com.ticket.core.support.exception.CoreException;
import com.ticket.core.support.exception.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
class SelectSeatUseCaseTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-04T01:00:00Z"),
            ZoneId.of("Asia/Seoul")
    );
    private static final LocalDateTime NOW = LocalDateTime.now(CLOCK);

    @Mock
    private SeatSelectionCoordinator seatSelectionCoordinator;

    @Mock
    private SeatSelectionAvailabilityValidator seatSelectionAvailabilityValidator;

    @Mock
    private SeatStatusEventPublisher seatEventPublisher;

    private SelectSeatUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new SelectSeatUseCase(
                seatSelectionCoordinator,
                seatSelectionAvailabilityValidator,
                seatEventPublisher,
                CLOCK
        );
    }

    @Test
    void select_then_publish_selected_event() {
        SelectSeatUseCase.Input input = new SelectSeatUseCase.Input(10L, 20L, 1L);

        useCase.execute(input);

        InOrder inOrder = inOrder(
                seatSelectionCoordinator,
                seatSelectionAvailabilityValidator,
                seatEventPublisher
        );
        inOrder.verify(seatSelectionAvailabilityValidator).validate(10L, 20L, NOW);
        inOrder.verify(seatSelectionCoordinator).select(10L, 20L, 1L);
        inOrder.verify(seatEventPublisher).publish(10L, 20L, SeatAction.SELECTED);
    }

    @Test
    void 예매가_마감된_회차는_좌석을_선택하지_않는다() {
        SelectSeatUseCase.Input input = new SelectSeatUseCase.Input(10L, 20L, 1L);
        doThrow(new CoreException(ErrorType.PERFORMANCE_IS_PAST))
                .when(seatSelectionAvailabilityValidator).validate(10L, 20L, NOW);

        assertThatThrownBy(() -> useCase.execute(input))
                .isInstanceOf(CoreException.class);

        verifyNoInteractions(seatSelectionCoordinator, seatEventPublisher);
    }
}
