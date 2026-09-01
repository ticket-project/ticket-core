package com.ticket.core.app.performanceseat.command;

import com.ticket.core.app.lock.LockKey;
import com.ticket.core.app.lock.LockManager;
import com.ticket.core.app.lock.LockOptions;
import com.ticket.core.domain.error.DomainErrorType;
import com.ticket.core.domain.hold.command.HoldManager;
import com.ticket.core.domain.performanceseat.command.SeatSelectionService;
import com.ticket.support.error.CoreException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class SeatSelectionCoordinator {

    /** 좌석 선택은 고빈도 경로라 경합 시 오래 기다리지 않고 빨리 실패한다. */
    private static final LockOptions SELECT_LOCK = LockOptions.waiting(Duration.ofMillis(500));

    private final LockManager lockManager;
    private final HoldManager holdManager;
    private final SeatSelectionService seatSelectionService;
    private final Clock clock;

    public void select(
            final Long performanceId,
            final Long seatId,
            final Long memberId,
            final LocalDateTime orderCloseTime
    ) {
        lockManager.withLock(java.util.List.of(LockKey.seat(performanceId, seatId)), SELECT_LOCK, () -> {
            if (LocalDateTime.now(clock).isAfter(orderCloseTime)) {
                throw new CoreException(DomainErrorType.PERFORMANCE_IS_PAST);
            }
            if (holdManager.isHeld(performanceId, seatId)) {
                throw new CoreException(DomainErrorType.SEAT_ALREADY_HOLD);
            }
            seatSelectionService.select(performanceId, seatId, memberId);
        });
    }
}
