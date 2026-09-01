package com.ticket.core.domain.performanceseat.support;

import com.ticket.support.error.CoreException;
import com.ticket.core.domain.performanceseat.repository.PerformanceSeatRepository;
import com.ticket.core.domain.error.DomainErrorType;
import com.ticket.core.domain.hold.command.HoldManager;
import com.ticket.core.domain.performanceseat.model.PerformanceSeatState;
import com.ticket.core.domain.performanceseat.query.model.SeatSelectionAvailabilitySnapshot;
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
                .orElseThrow(() -> new CoreException(DomainErrorType.SEAT_MISMATCH_IN_PERFORMANCE));

        if (seat.state() != PerformanceSeatState.AVAILABLE) {
            throw new CoreException(DomainErrorType.NOT_EXIST_AVAILABLE_SEAT);
        }
        if (holdManager.isHeld(performanceId, seatId)) {
            throw new CoreException(DomainErrorType.SEAT_ALREADY_HOLD);
        }
    }
}
