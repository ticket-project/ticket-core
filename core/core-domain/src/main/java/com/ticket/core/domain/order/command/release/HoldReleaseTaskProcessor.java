package com.ticket.core.domain.order.command.release;

import com.ticket.core.domain.hold.command.HoldManager;
import com.ticket.core.domain.performanceseat.command.SeatStatusPublisher;
import com.ticket.core.support.lock.DistributedLock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class HoldReleaseTaskProcessor {

    private final HoldManager holdManager;
    private final SeatStatusPublisher seatStatusPublisher;

    @DistributedLock(
            prefix = "hold",
            dynamicKey = "#task.seatIds().![#task.performanceId() + ':' + #this]"
    )
    public void process(final HoldReleaseTask task) {
        final List<Long> releasedSeatIds = holdManager.release(
                task.performanceId(),
                task.holdKey(),
                task.seatIds()
        );
        if (releasedSeatIds.isEmpty()) {
            return;
        }
        seatStatusPublisher.publishReleased(task.performanceId(), releasedSeatIds);
    }
}
