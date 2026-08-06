package com.ticket.core.domain.performanceseat.command;

import com.ticket.core.domain.performanceseat.query.model.SeatSelectionAvailabilityView;
import com.ticket.core.domain.performanceseat.support.SeatStatusEventPublisher;
import com.ticket.core.domain.performanceseat.support.SeatSelectionAvailabilityValidator;
import com.ticket.core.domain.performanceseat.support.SeatStatusMessage.SeatAction;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class SelectSeatUseCase {

    private final SeatSelectionCoordinator seatSelectionCoordinator;
    private final SeatSelectionAvailabilityValidator seatSelectionAvailabilityValidator;
    private final SeatStatusEventPublisher seatEventPublisher;
    private final Clock clock;

    public record Input(Long performanceId, Long seatId, Long memberId) {}

    public void execute(final Input input) {
        final SeatSelectionAvailabilityView availability = seatSelectionAvailabilityValidator.validate(
                input.performanceId(),
                input.seatId(),
                LocalDateTime.now(clock)
        );
        seatSelectionCoordinator.select(
                input.performanceId(),
                input.seatId(),
                input.memberId(),
                availability.orderCloseTime()
        );
        seatEventPublisher.publish(input.performanceId(), input.seatId(), SeatAction.SELECTED);
    }
}
