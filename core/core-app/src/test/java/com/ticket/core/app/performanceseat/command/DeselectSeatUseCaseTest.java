package com.ticket.core.app.performanceseat.command;

import com.ticket.core.domain.performanceseat.support.SeatStatusMessage;
import com.ticket.core.domain.performanceseat.command.SeatSelectionService;
import com.ticket.core.domain.performanceseat.support.SeatStatusEventPublisher;
import com.ticket.core.domain.performanceseat.support.SeatStatusMessage.SeatAction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.inOrder;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
class DeselectSeatUseCaseTest {

    @Mock
    private SeatSelectionService seatSelectionService;

    @Mock
    private SeatStatusEventPublisher seatEventPublisher;

    @InjectMocks
    private DeselectSeatUseCase useCase;

    @Test
    void deselect_then_publish_deselected_event() {
        DeselectSeatUseCase.Input input = new DeselectSeatUseCase.Input(10L, 20L, 1L);

        useCase.execute(input);

        InOrder inOrder = inOrder(seatSelectionService, seatEventPublisher);
        inOrder.verify(seatSelectionService).deselect(10L, 20L, 1L);
        inOrder.verify(seatEventPublisher).publish(10L, 20L, SeatAction.DESELECTED);
    }
}
