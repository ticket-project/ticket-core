package com.ticket.core.app.performanceseat.command;

import com.ticket.core.domain.performanceseat.command.SeatSelectionService;
import com.ticket.core.app.performanceseat.event.SeatStatusEvent.SeatStatusAction;
import com.ticket.core.app.performanceseat.event.SeatStatusEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.ticket.core.app.support.validation.RequiredInput;

@Service
@RequiredArgsConstructor
public class DeselectSeatUseCase {

    private final SeatSelectionService seatSelectionService;
    private final SeatStatusEventPublisher seatEventPublisher;

    public record Input(Long performanceId, Long seatId, Long memberId) {
        public Input {
            RequiredInput.positiveId(performanceId, "performanceId");
            RequiredInput.positiveId(seatId, "seatId");
            RequiredInput.positiveId(memberId, "memberId");
        }
    }

    public void execute(final Input input) {
        seatSelectionService.deselect(input.performanceId(), input.seatId(), input.memberId());
        seatEventPublisher.publish(input.performanceId(), input.seatId(), SeatStatusAction.DESELECTED);
    }
}
