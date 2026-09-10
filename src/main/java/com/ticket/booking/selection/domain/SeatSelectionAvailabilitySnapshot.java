package com.ticket.booking.selection.domain;

import com.ticket.booking.domain.PerformanceSeatState;

/**
 * 좌석 선택 가능 여부 판정에 필요한 좌석 정보.
 *
 * <p>예매 가능 시각은 여기 담지 않는다. 회차 정책은 캐시할 수 있고 좌석 상태는 결제 확정으로
 * 런타임에 변하므로, 두 값을 한 쿼리로 묶으면 정책만 캐시할 수 없게 된다.
 */
public record SeatSelectionAvailabilitySnapshot(
        Long performanceSeatId,
        PerformanceSeatState state
) {
}
