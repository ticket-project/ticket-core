package com.ticket.booking.seat.port;

import org.jspecify.annotations.Nullable;

import com.ticket.booking.seat.port.SeatStatusEvent.SeatStatusAction;

/** 좌석 상태 알림을 모든 인스턴스의 사용자에게 보낸다. 호출자는 좌석 락 안에서 부른다 — 상태 변경과 발행 사이의 순서를 락이 지킨다. */
public interface SeatStatusEventPublisher {
    /**
     * 방금 바꾼 상태를 그대로 알린다(SELECTED, HELD).
     *
     * <p>performanceSeatId는 회차 판매 좌석을 찾지 못하면 null이다 — 표시용 값이라 없어도 발행을 막지 않는다.
     */
    void publish(Long performanceId, @Nullable Long performanceSeatId, Long seatId, SeatStatusAction action);

    /** 지금 아무도 선택·선점하지 않은 좌석일 때만 알린다(DESELECTED, RELEASED). 확인과 발행이 한 번에 일어나므로 남이 이미 차지한 좌석을 비었다고 알리지 않는다. */
    void publishIfFree(Long performanceId, @Nullable Long performanceSeatId, Long seatId, SeatStatusAction action);
}
