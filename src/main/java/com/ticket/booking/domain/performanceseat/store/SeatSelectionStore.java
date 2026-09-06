package com.ticket.booking.domain.performanceseat.store;

import java.time.Duration;
import java.util.List;
import java.util.Set;

public interface SeatSelectionStore {

    boolean selectIfAbsent(Long performanceId, Long seatId, String memberId, Duration ttl);

    String getHolder(Long performanceId, Long seatId);

    boolean releaseIfOwned(Long performanceId, Long seatId, String memberId);

    List<Long> releaseAllByMember(Long performanceId, String memberId);

    Set<Long> getSelectingSeatIds(Long performanceId);
}
