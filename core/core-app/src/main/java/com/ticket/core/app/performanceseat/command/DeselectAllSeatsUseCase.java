package com.ticket.core.app.performanceseat.command;

import com.ticket.core.domain.performanceseat.support.SeatStatusMessage;
import com.ticket.core.domain.performanceseat.command.SeatSelectionService;
import com.ticket.core.domain.performanceseat.command.DeselectedSeatIds;
import com.ticket.core.domain.member.query.MemberFinder;
import com.ticket.core.domain.performanceseat.support.SeatStatusEventPublisher;
import com.ticket.core.domain.performanceseat.support.SeatStatusMessage.SeatAction;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DeselectAllSeatsUseCase {

    private final MemberFinder memberFinder;
    private final SeatSelectionService seatSelectionService;
    private final SeatStatusEventPublisher seatEventPublisher;

    public record Input(Long performanceId, Long memberId) {}

    public void execute(final Input input) {
        memberFinder.findActiveMemberById(input.memberId());
        final DeselectedSeatIds seatIds = seatSelectionService.deselectAll(input.performanceId(), input.memberId());
        seatIds.forEach(seatId -> seatEventPublisher.publish(input.performanceId(), seatId, SeatAction.DESELECTED));
    }
}
