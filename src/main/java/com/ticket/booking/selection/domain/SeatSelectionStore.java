package com.ticket.booking.selection.domain;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

public interface SeatSelectionStore {
    /**
     * 좌석이 비어 있고 회원의 선택 좌석 수가 한도 안이면 선택한다. 좌석 확인·한도 확인·기록이 원자적으로 일어난다 — 같은 회원이 다른 좌석을 동시에 선택해도 한도를 넘지 않는다.
     *
     * @param maxSeatCount 회원이 이 회차에서 동시에 선택할 수 있는 좌석 수. null이면 제한하지 않는다
     */
    SelectResult selectIfAbsent(
            Long performanceId, Long seatId, String memberId, Duration ttl, @Nullable Integer maxSeatCount);

    String getHolder(Long performanceId, Long seatId);

    boolean releaseIfOwned(Long performanceId, Long seatId, String memberId);

    List<Long> releaseAllByMember(Long performanceId, String memberId);

    Set<Long> getSelectingSeatIds(Long performanceId);

    /** 이 회원이 지금 선택 중인 좌석. 만료된 선택은 포함하지 않는다. */
    Set<Long> getSelectedSeatIdsByMember(Long performanceId, String memberId);

    enum SelectResult {
        SELECTED,
        ALREADY_SELECTED,
        LIMIT_EXCEEDED
    }
}
