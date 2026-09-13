package com.ticket.booking.order.application;

import java.time.LocalDateTime;
import java.util.List;

/**
 * hold 후속 처리에 필요한 주문 정보만 담은 값이다. 짧은 읽기 트랜잭션 안에서 완전히 채워지므로, 트랜잭션 밖의 Redis·WebSocket 작업이 lazy 연관을 다시
 * 건드릴 일이 없다.
 *
 * @param seatIds 물리 좌석 id. OrderSeat의 {@code id ASC} 정렬을 그대로 유지한다 — hold 생성 후처리가 이 순서를 그대로 쓴다
 */
public record OrderHoldSnapshot(Long performanceId, List<Long> seatIds, LocalDateTime expiresAt) {
    public OrderHoldSnapshot {
        seatIds = List.copyOf(seatIds);
    }
}
