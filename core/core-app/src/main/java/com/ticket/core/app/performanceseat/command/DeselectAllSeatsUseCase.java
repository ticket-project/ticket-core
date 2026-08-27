package com.ticket.core.app.performanceseat.command;

import com.ticket.core.domain.performanceseat.support.SeatStatusMessage;
import com.ticket.core.domain.performanceseat.command.SeatSelectionService;
import com.ticket.core.domain.performanceseat.command.DeselectedSeatIds;
import com.ticket.core.domain.member.repository.MemberRepository;
import com.ticket.core.domain.performanceseat.support.SeatStatusEventPublisher;
import com.ticket.core.domain.performanceseat.support.SeatStatusMessage.SeatAction;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.ticket.core.app.support.validation.RequiredInput;

@Service
@RequiredArgsConstructor
public class DeselectAllSeatsUseCase {

    private final MemberRepository memberRepository;
    private final SeatSelectionService seatSelectionService;
    private final SeatStatusEventPublisher seatEventPublisher;

    public record Input(Long performanceId, Long memberId) {
        public Input {
            RequiredInput.positiveId(performanceId, "performanceId");
            RequiredInput.positiveId(memberId, "memberId");
        }
    }

    public void execute(final Input input) {
        memberRepository.getActiveById(input.memberId());
        final DeselectedSeatIds seatIds = seatSelectionService.deselectAll(input.performanceId(), input.memberId());
        seatIds.forEach(seatId -> seatEventPublisher.publish(input.performanceId(), seatId, SeatAction.DESELECTED));
    }
}
