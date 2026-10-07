package com.ticket.booking.hold.domain;

import java.time.Duration;
import java.util.List;
import java.util.Set;

/** hold의 임시 상태 저장 포트다. key 형식과 TTL 적용 방식은 구현이 결정한다. */
public interface HoldStore {
    /** 좌석 키가 없을 때만 저장한다. 충돌하면 앞서 쓴 이 hold의 좌석을 보상하고 false를 반환한다. */
    boolean saveIfAbsent(Hold hold, Duration ttl);

    /** 좌석에 아직 이 holdKey가 남아 있을 때만 지운다. 반복해도 그사이 다른 hold가 잡은 좌석은 건드리지 않는다. */
    void release(Long performanceId, String holdKey, List<Long> seatIds);

    Set<Long> getHoldingSeatIds(Long performanceId);

    boolean isHeld(Long performanceId, Long seatId);

    boolean isHeldBy(Long performanceId, Long seatId, String holdKey);
}
