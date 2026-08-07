package com.ticket.core.domain.order.command.release;

import java.util.List;

public record HoldReleaseTask(
        Long performanceId,
        String holdKey,
        List<Long> seatIds
) {
}
