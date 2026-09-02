package com.ticket.booking.internal.application.performanceseat.command;

import com.ticket.booking.internal.domain.performanceseat.command.SeatSelectionService;
import com.ticket.booking.internal.domain.performanceseat.command.DeselectedSeatIds;
import com.ticket.identity.MemberLookup;
import com.ticket.booking.internal.application.performanceseat.event.SeatStatusEvent.SeatStatusAction;
import com.ticket.booking.internal.application.performanceseat.event.SeatStatusEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.ticket.core.app.support.validation.RequiredInput;

@Service
@RequiredArgsConstructor
public class DeselectAllSeatsUseCase {

    private final MemberLookup memberLookup;
    private final SeatSelectionService seatSelectionService;
    private final SeatStatusEventPublisher seatEventPublisher;

    public record Input(Long performanceId, Long memberId) {
        public Input {
            RequiredInput.positiveId(performanceId, "performanceId");
            RequiredInput.positiveId(memberId, "memberId");
        }
    }

    public void execute(final Input input) {
        memberLookup.requireActive(input.memberId());
        final DeselectedSeatIds seatIds = seatSelectionService.deselectAll(input.performanceId(), input.memberId());
        seatIds.forEach(seatId -> seatEventPublisher.publish(input.performanceId(), seatId, SeatStatusAction.DESELECTED));
    }
}
