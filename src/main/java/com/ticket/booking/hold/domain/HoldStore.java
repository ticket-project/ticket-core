package com.ticket.booking.hold.domain;

import com.ticket.booking.hold.domain.Hold;

import java.time.Duration;
import java.util.List;
import java.util.Set;

/**
 * hold의 임시 상태 저장 포트다. key 형식과 TTL 적용 방식은 구현이 결정한다.
 */
public interface HoldStore {

    void save(Hold hold, Duration ttl);

    List<Long> release(Long performanceId, String holdKey, List<Long> seatIds);

    Set<Long> getHoldingSeatIds(Long performanceId);

    boolean isHeld(Long performanceId, Long seatId);

    boolean isHeldBy(Long performanceId, Long seatId, String holdKey);
}
