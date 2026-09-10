package com.ticket.booking.hold.application;

import java.util.List;

/**
 * hold 해제 후처리의 입력이다.
 *
 * @param holdReleased Redis 해제까지 이미 끝났는지 여부. 재시도에서 해제를 반복하지 않기 위해 본다
 */
public record HoldReleaseTask(
        Long performanceId,
        String holdKey,
        List<Long> seatIds,
        boolean holdReleased
) {
}
