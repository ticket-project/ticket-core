package com.ticket.core.domain.performanceseat.support;

import com.ticket.core.domain.hold.command.HoldManager;
import com.ticket.core.domain.performanceseat.model.PerformanceSeatState;
import com.ticket.core.domain.performanceseat.query.SeatSelectionAvailabilityQueryRepository;
import com.ticket.core.domain.performanceseat.query.model.SeatSelectionAvailabilityView;
import com.ticket.core.support.exception.CoreException;
import com.ticket.core.support.exception.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class SeatSelectionAvailabilityValidator {

    private final HoldManager holdManager;
    private final SeatSelectionAvailabilityQueryRepository queryRepository;

    public void validate(final Long performanceId, final Long seatId, final LocalDateTime now) {
        final SeatSelectionAvailabilityView availability = queryRepository
                .findForSelection(performanceId, seatId)
                .orElseThrow(() -> new CoreException(
                        ErrorType.NOT_FOUND_DATA,
                        "공연을 찾을 수 없습니다. id=" + performanceId
                ));

        if (availability.orderOpenTime() == null || now.isBefore(availability.orderOpenTime())) {
            throw new CoreException(ErrorType.NOT_YET_RESERVE_TIME);
        }
        if (availability.orderCloseTime() == null || now.isAfter(availability.orderCloseTime())) {
            throw new CoreException(ErrorType.PERFORMANCE_IS_PAST);
        }
        if (availability.performanceSeatId() == null) {
            throw new CoreException(ErrorType.SEAT_MISMATCH_IN_PERFORMANCE);
        }
        if (availability.state() != PerformanceSeatState.AVAILABLE) {
            throw new CoreException(ErrorType.NOT_EXIST_AVAILABLE_SEAT);
        }
        if (holdManager.isHeld(performanceId, seatId)) {
            throw new CoreException(ErrorType.SEAT_ALREADY_HOLD);
        }
    }
}
