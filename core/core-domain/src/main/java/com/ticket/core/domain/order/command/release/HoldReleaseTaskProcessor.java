package com.ticket.core.domain.order.command.release;

import com.ticket.core.domain.hold.command.HoldManager;
import com.ticket.core.domain.performanceseat.command.SeatSelectionService;
import com.ticket.core.domain.performanceseat.command.SeatStatusPublisher;
import com.ticket.core.support.lock.DistributedLock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class HoldReleaseTaskProcessor {

    private final HoldManager holdManager;
    private final SeatSelectionService seatSelectionService;
    private final SeatStatusPublisher seatStatusPublisher;
    private final HoldReleaseOutboxTransactionService transactionService;

    @DistributedLock(
            prefix = "hold",
            dynamicKey = "#task.seatIds().![#task.performanceId() + ':' + #this]"
    )
    public void process(final Long outboxId, final HoldReleaseTask task, final LocalDateTime now) {
        releaseHoldOnce(outboxId, task, now);
        final List<Long> publishableSeatIds = findCurrentlyAvailableSeats(task);
        if (publishableSeatIds.isEmpty()) {
            return;
        }
        seatStatusPublisher.publishReleased(task.performanceId(), publishableSeatIds);
    }

    private void releaseHoldOnce(
            final Long outboxId,
            final HoldReleaseTask task,
            final LocalDateTime now
    ) {
        if (task.holdReleased()) {
            return;
        }
        holdManager.release(task.performanceId(), task.holdKey(), task.seatIds());
        transactionService.markHoldReleased(outboxId, now);
    }

    private List<Long> findCurrentlyAvailableSeats(final HoldReleaseTask task) {
        final Set<Long> selectedSeatIds = seatSelectionService.getSelectingSeatIds(task.performanceId());
        return task.seatIds().stream()
                .filter(seatId -> !holdManager.isHeld(task.performanceId(), seatId))
                .filter(seatId -> !selectedSeatIds.contains(seatId))
                .toList();
    }
}
