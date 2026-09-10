package com.ticket.booking.selection.domain;

import com.ticket.booking.domain.PerformanceSeatRepository;
import com.ticket.booking.domain.HoldManager;
import com.ticket.booking.domain.PerformanceSeatState;
import com.ticket.booking.selection.domain.SeatSelectionAvailabilitySnapshot;
import com.ticket.booking.exception.NoAvailableSeatException;
import com.ticket.booking.exception.SeatAlreadyHoldException;
import com.ticket.booking.exception.SeatMismatchInPerformanceException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SeatSelectionAvailabilityValidator {

    private final HoldManager holdManager;
    private final PerformanceSeatRepository performanceSeatRepository;

    /**
     * 좌석 자체가 선택 가능한지 확인한다. 예매 가능 시각과 대기열 입장은 호출자가 회차 정책으로
     * 이미 판정했으므로 여기서 다시 보지 않는다.
     *
     * @return 검증된 좌석의 performanceSeatId. 호출자가 WebSocket 이벤트 등 외부 식별자가 필요한
     * 곳에 다시 쓸 수 있도록 돌려준다 — 이미 이 조회에서 로드했으므로 추가 조회가 필요 없다.
     */
    public Long validate(final Long performanceId, final Long seatId) {
        final SeatSelectionAvailabilitySnapshot seat = performanceSeatRepository
                .findSelectableSeat(performanceId, seatId)
                .orElseThrow(() -> new SeatMismatchInPerformanceException());

        if (seat.state() != PerformanceSeatState.AVAILABLE) {
            throw new NoAvailableSeatException();
        }
        if (holdManager.isHeld(performanceId, seatId)) {
            throw new SeatAlreadyHoldException();
        }
        return seat.performanceSeatId();
    }
}
