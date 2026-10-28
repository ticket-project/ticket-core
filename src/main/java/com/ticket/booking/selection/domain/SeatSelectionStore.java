package com.ticket.booking.selection.domain;

import java.time.Duration;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import com.ticket.booking.seat.port.SeatStatusEvent;

public interface SeatSelectionStore {
    /**
     * 좌석이 선점되지 않았고 비어 있고 회원의 선택 좌석 수가 한도 안이면 선택하고 {@code selected}를 발행한다. 확인·기록·발행이 원자적으로 일어난다 — 같은 회원이 다른 좌석을 동시에 선택해도
     * 한도를 넘지 않고, 알림 순서가 상태가 바뀐 순서와 같다.
     *
     * @param maxSeatCount 회원이 이 회차에서 동시에 선택할 수 있는 좌석 수. null이면 제한하지 않는다
     */
    SelectResult selectIfAbsent(
            Long performanceId,
            Long seatId,
            String memberId,
            Duration ttl,
            @Nullable Integer maxSeatCount,
            SeatStatusEvent selected);

    @Nullable
    String getHolder(Long performanceId, Long seatId);

    /**
     * 이 회원의 선택이면 지운다. 지웠고 그 좌석이 선점되지 않았으면 {@code deselected}를 발행한다(null이면 발행하지 않는다). 확인·삭제·발행이 원자적이다.
     *
     * @return 이 호출이 지웠으면 true
     */
    boolean releaseIfOwned(Long performanceId, Long seatId, String memberId, @Nullable SeatStatusEvent deselected);

    /** 선택도 선점도 없는 좌석이면 {@code deselected}를 발행한다. 확인과 발행이 원자적이라 그 사이에 다시 선택된 좌석을 비었다고 알리지 않는다. */
    void publishIfFree(Long performanceId, Long seatId, SeatStatusEvent deselected);

    Set<Long> getSelectingSeatIds(Long performanceId);

    /** 이 회원이 지금 선택 중인 좌석. 만료된 선택은 포함하지 않는다. */
    Set<Long> getSelectedSeatIdsByMember(Long performanceId, String memberId);

    /** 이 회원의 선택 중 최근에 시간이 지나 풀린 좌석. 직접 해제했거나 오래전에 만료된 좌석은 포함하지 않는다. */
    Set<Long> getRecentlyExpiredSeatIdsByMember(Long performanceId, String memberId);

    enum SelectResult {
        SELECTED,
        ALREADY_SELECTED,
        LIMIT_EXCEEDED,
        HELD
    }
}
