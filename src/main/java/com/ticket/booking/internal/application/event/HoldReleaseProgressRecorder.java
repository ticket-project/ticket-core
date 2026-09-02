package com.ticket.booking.internal.application.event;

import java.time.LocalDateTime;

/**
 * hold 해제 후처리의 중간 진행 상태를 기록한다.
 *
 * <p>Redis 해제까지 끝났음을 발행 전에 남겨, 발행이 실패한 재시도에서 Redis 해제를 반복하지 않는다.
 */
public interface HoldReleaseProgressRecorder {

    void recordHoldReleased(Long eventId, LocalDateTime releasedAt);
}
