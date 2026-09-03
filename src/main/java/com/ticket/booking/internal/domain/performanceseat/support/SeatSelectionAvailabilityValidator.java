package com.ticket.booking.internal.domain.performanceseat.support;

import com.ticket.booking.internal.domain.performanceseat.repository.PerformanceSeatRepository;
import com.ticket.booking.internal.domain.hold.command.HoldManager;
import com.ticket.booking.internal.domain.performanceseat.model.PerformanceSeatState;
import com.ticket.booking.internal.domain.performanceseat.query.model.SeatSelectionAvailabilitySnapshot;
import com.ticket.booking.internal.exception.NoAvailableSeatException;
import com.ticket.booking.internal.exception.SeatAlreadyHoldException;
import com.ticket.booking.internal.exception.SeatMismatchInPerformanceException;
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
     */
    public void validate(final Long performanceId, final Long seatId) {
        final SeatSelectionAvailabilitySnapshot seat = performanceSeatRepository
                .findSelectableSeat(performanceId, seatId)
                .orElseThrow(() -> new SeatMismatchInPerformanceException());

        if (seat.state() != PerformanceSeatState.AVAILABLE) {
            throw new NoAvailableSeatException();
        }
        if (holdManager.isHeld(performanceId, seatId)) {
            throw new SeatAlreadyHoldException();
        }
    }
}
