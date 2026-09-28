package com.ticket.booking.event;

import java.util.List;

/** hold 해제 후처리의 입력이다. */
public record HoldReleaseTask(Long performanceId, String holdKey, List<Long> seatIds) {}
