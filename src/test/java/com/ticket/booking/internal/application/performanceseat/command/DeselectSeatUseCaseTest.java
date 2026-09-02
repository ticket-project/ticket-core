package com.ticket.booking.internal.application.performanceseat.command;

import com.ticket.booking.internal.domain.performanceseat.command.SeatSelectionService;
import com.ticket.booking.internal.application.performanceseat.event.SeatStatusEvent.SeatStatusAction;
import com.ticket.booking.internal.application.performanceseat.event.SeatStatusEventPublisher;
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
        inOrder.verify(seatEventPublisher).publish(10L, 20L, SeatStatusAction.DESELECTED);
    }
}
