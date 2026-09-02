package com.ticket.booking.internal.application.event;

import java.util.List;

/**
 * 주문 취소·만료로 hold를 해제해야 한다는 요청 payload다.
 *
 * <p>{@link HoldLifecycleEventPublisher#publishHoldReleased}가 이 값을 받아 hold 해제 후속 처리를
 * 기록한다.
 */
public record HoldReleaseRequest(
        Long performanceId,
        String holdKey,
        List<Long> seatIds
) {
}
