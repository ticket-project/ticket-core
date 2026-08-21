package com.ticket.core.domain.order.command.release;

import com.ticket.core.domain.hold.model.HoldReleaseReason;

import java.util.List;

public record HoldReleaseTask(
        Long performanceId,
        String holdKey,
        List<Long> seatIds,
        boolean holdReleased,
        HoldReleaseReason reason
) {
}
