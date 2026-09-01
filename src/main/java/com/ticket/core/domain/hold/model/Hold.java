package com.ticket.core.domain.hold.model;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 좌석 선점(hold)이다. 좌석 점유의 기준이며 만료 시각까지만 유효하다.
 *
 * <p>유효 시간은 도메인이 결정하고 {@code expiresAt}으로 표현한다. 이 값을 Redis TTL로 적용하는
 * 일은 core-infra가 한다. 같은 업무 TTL을 infra에 다시 정의하지 않는다.
 */
public record Hold(
        String holdKey,
        Long memberId,
        Long performanceId,
        List<Long> seatIds,
        LocalDateTime expiresAt
) {

    public static Hold create(
            final String holdKey,
            final Long memberId,
            final Long performanceId,
            final List<Long> seatIds,
            final LocalDateTime now,
            final Duration ttl
    ) {
        return new Hold(holdKey, memberId, performanceId, List.copyOf(seatIds), now.plus(ttl));
    }

    /**
     * 이 hold가 시작된 시각이다. 남은 시간 계산이 아니라 이력 기록에 쓴다.
     */
    public LocalDateTime startedAt(final Duration holdDuration) {
        return expiresAt.minus(holdDuration);
    }
}
