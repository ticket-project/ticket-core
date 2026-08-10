package com.ticket.core.domain.hold.store;

import com.ticket.core.domain.hold.model.HoldSnapshot;

import java.time.Duration;
import java.util.List;
import java.util.Set;

public interface HoldStore {

    void save(HoldSnapshot snapshot, Duration ttl);

    List<Long> release(Long performanceId, String holdKey, List<Long> seatIds);

    Set<Long> getHoldingSeatIds(Long performanceId);

    boolean isHeld(Long performanceId, Long seatId);

    boolean isHeldBy(Long performanceId, Long seatId, String holdKey);
}
