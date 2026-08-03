package com.ticket.core.domain.performanceseat.command;

import com.ticket.core.domain.performance.query.PerformanceFinder;
import com.ticket.core.domain.performanceseat.support.SeatStatusEventPublisher;
import com.ticket.core.support.lock.DistributedLock;
import com.ticket.core.domain.performanceseat.support.SeatSelectionAvailabilityValidator;
import com.ticket.core.domain.performanceseat.support.SeatStatusMessage.SeatAction;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class SelectSeatUseCase {

    private final PerformanceFinder performanceFinder;
    private final SeatSelectionService seatSelectionService;
    private final SeatSelectionAvailabilityValidator seatSelectionAvailabilityValidator;
    private final SeatStatusEventPublisher seatEventPublisher;
    private final Clock clock;

    public record Input(Long performanceId, Long seatId, Long memberId) {}

    @DistributedLock(
            prefix = "hold",
            dynamicKey = "#input.performanceId() + ':' + #input.seatId()"
    )
    public void execute(final Input input) {
        performanceFinder.findValidPerformanceById(input.performanceId(), LocalDateTime.now(clock));
        seatSelectionAvailabilityValidator.validate(input.performanceId(), input.seatId());
        seatSelectionService.select(input.performanceId(), input.seatId(), input.memberId());
        seatEventPublisher.publish(input.performanceId(), input.seatId(), SeatAction.SELECTED);
    }
}
