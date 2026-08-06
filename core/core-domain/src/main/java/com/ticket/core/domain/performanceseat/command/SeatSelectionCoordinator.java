package com.ticket.core.domain.performanceseat.command;

import com.ticket.core.domain.hold.command.HoldManager;
import com.ticket.core.support.exception.CoreException;
import com.ticket.core.support.exception.ErrorType;
import com.ticket.core.support.lock.DistributedLock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SeatSelectionCoordinator {

    private final HoldManager holdManager;
    private final SeatSelectionService seatSelectionService;

    @DistributedLock(
            prefix = "hold",
            dynamicKey = "#performanceId + ':' + #seatId",
            waitTime = 500L
    )
    public void select(final Long performanceId, final Long seatId, final Long memberId) {
        if (holdManager.isHeld(performanceId, seatId)) {
            throw new CoreException(ErrorType.SEAT_ALREADY_HOLD);
        }
        seatSelectionService.select(performanceId, seatId, memberId);
    }
}
