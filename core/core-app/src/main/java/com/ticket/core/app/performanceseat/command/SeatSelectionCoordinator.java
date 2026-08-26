package com.ticket.core.app.performanceseat.command;

import com.ticket.support.error.CoreException;
import com.ticket.core.domain.error.DomainErrorType;
import com.ticket.core.domain.performanceseat.command.SeatSelectionService;
import com.ticket.core.domain.hold.command.HoldManager;
import com.ticket.core.support.lock.DistributedLock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class SeatSelectionCoordinator {

    private final HoldManager holdManager;
    private final SeatSelectionService seatSelectionService;
    private final Clock clock;

    @DistributedLock(
            prefix = "hold",
            dynamicKey = "#performanceId + ':' + #seatId",
            waitTime = 500L
    )
    public void select(
            final Long performanceId,
            final Long seatId,
            final Long memberId,
            final LocalDateTime orderCloseTime
    ) {
        if (LocalDateTime.now(clock).isAfter(orderCloseTime)) {
            throw new CoreException(DomainErrorType.PERFORMANCE_IS_PAST);
        }
        if (holdManager.isHeld(performanceId, seatId)) {
            throw new CoreException(DomainErrorType.SEAT_ALREADY_HOLD);
        }
        seatSelectionService.select(performanceId, seatId, memberId);
    }
}
