package com.ticket.core.app.performanceseat.command;

import com.ticket.core.app.error.ApplicationErrorType;
import com.ticket.support.error.CoreException;
import com.ticket.core.domain.performanceseat.command.SeatSelectionService;
import com.ticket.core.domain.performanceseat.command.DeselectedSeatIds;
import com.ticket.core.domain.member.repository.MemberRepository;
import com.ticket.core.app.performanceseat.event.SeatStatusEvent.SeatStatusAction;
import com.ticket.core.app.performanceseat.event.SeatStatusEventPublisher;
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
        memberRepository.findActiveById(input.memberId())
                .orElseThrow(() -> new CoreException(ApplicationErrorType.DATA_NOT_FOUND));
        final DeselectedSeatIds seatIds = seatSelectionService.deselectAll(input.performanceId(), input.memberId());
        seatIds.forEach(seatId -> seatEventPublisher.publish(input.performanceId(), seatId, SeatStatusAction.DESELECTED));
    }
}
