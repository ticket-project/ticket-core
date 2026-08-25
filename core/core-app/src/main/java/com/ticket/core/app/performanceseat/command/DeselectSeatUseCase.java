package com.ticket.core.app.performanceseat.command;

import com.ticket.core.domain.performanceseat.support.SeatStatusMessage;
import com.ticket.core.domain.performanceseat.command.SeatSelectionService;
import com.ticket.core.domain.performanceseat.support.SeatStatusEventPublisher;
import com.ticket.core.domain.performanceseat.support.SeatStatusMessage.SeatAction;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DeselectSeatUseCase {

    private final SeatSelectionService seatSelectionService;
    private final SeatStatusEventPublisher seatEventPublisher;

    public record Input(Long performanceId, Long seatId, Long memberId) {}

    public void execute(final Input input) {
        seatSelectionService.deselect(input.performanceId(), input.seatId(), input.memberId());
        seatEventPublisher.publish(input.performanceId(), input.seatId(), SeatAction.DESELECTED);
    }
}
