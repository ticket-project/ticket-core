package com.ticket.booking.hold.domain;

import java.time.Duration;
import java.util.List;
import java.util.Set;

/** hold의 임시 상태 저장 포트다. key 형식과 TTL 적용 방식은 구현이 결정한다. */
public interface HoldStore {
    /** 요청한 좌석 키가 하나도 없을 때만 모두 저장한다. 하나라도 이미 잡혀 있으면 아무것도 쓰지 않는다. */
    SaveResult saveIfAbsent(Hold hold, Duration ttl);

    /** 좌석에 아직 이 holdKey가 남아 있을 때만 지운다. 반복해도 그사이 다른 hold가 잡은 좌석은 건드리지 않는다. */
    void release(Long performanceId, String holdKey, List<Long> seatIds);

    Set<Long> getHoldingSeatIds(Long performanceId);

    boolean isHeld(Long performanceId, Long seatId);

    boolean isHeldBy(Long performanceId, Long seatId, String holdKey);

    /**
     * 선점 저장 결과다. 충돌한 좌석의 선점이 같은 회원 것이면 그 회원의 다른 주문 생성이 진행 중이거나 이미 끝난 것이다 — 선점은 주문 생성만 만든다. 회원·회차 분산 락 없이도 같은 회원의 동시 주문에
     * "진행 중인 주문" 오류를 주려고 둘을 가른다.
     */
    enum SaveResult {
        SAVED,
        HELD_BY_OTHER,
        HELD_BY_SAME_MEMBER
    }
}
