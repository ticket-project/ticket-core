package com.ticket.booking.application.event;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * hold 해제 후처리의 중간 진행 상태를 기록한다.
 *
 * <p>Redis 해제까지 끝났음을 발행 전에 남겨, 같은 event가 재전달돼도 Redis 해제를 반복하지 않는다.
 * {@code eventId}는 Modulith event publication의 식별자이며 이 진행 상태의 unique key다.
 */
public interface HoldReleaseProgressRecorder {

    boolean isReleased(UUID eventId);

    void recordHoldReleased(UUID eventId, LocalDateTime releasedAt);
}
