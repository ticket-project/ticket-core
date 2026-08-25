package com.ticket.core.app.performanceseat.command;

import com.ticket.core.domain.performanceseat.support.SeatStatusMessage;
import com.ticket.core.domain.performance.query.PerformanceBookingPolicyFinder;
import com.ticket.core.domain.performance.query.BookingPolicyValidator;
import com.ticket.core.domain.performance.query.model.PerformanceBookingPolicyView;
import com.ticket.core.domain.performanceseat.support.SeatStatusEventPublisher;
import com.ticket.core.domain.performanceseat.support.SeatSelectionAvailabilityValidator;
import com.ticket.core.domain.performanceseat.support.SeatStatusMessage.SeatAction;
import com.ticket.core.domain.queue.AdmissionGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class SelectSeatUseCase {

    private final PerformanceBookingPolicyFinder performanceBookingPolicyFinder;
    private final SeatSelectionCoordinator seatSelectionCoordinator;
    private final SeatSelectionAvailabilityValidator seatSelectionAvailabilityValidator;
    private final AdmissionGuard admissionGuard;
    private final SeatStatusEventPublisher seatEventPublisher;
    private final Clock clock;

    public record Input(Long performanceId, Long seatId, Long memberId, String admissionToken) {}

    public void execute(final Input input) {
        final LocalDateTime now = LocalDateTime.now(clock);

        final PerformanceBookingPolicyView policy =
                performanceBookingPolicyFinder.findById(input.performanceId());
        BookingPolicyValidator.ensureBookingOpen(policy, now);
        ensureAdmitted(policy, input, now);

        seatSelectionAvailabilityValidator.validate(input.performanceId(), input.seatId());

        seatSelectionCoordinator.select(
                input.performanceId(),
                input.seatId(),
                input.memberId(),
                policy.orderCloseTime()
        );
        seatEventPublisher.publish(input.performanceId(), input.seatId(), SeatAction.SELECTED);
    }

    private void ensureAdmitted(
            final PerformanceBookingPolicyView policy,
            final Input input,
            final LocalDateTime now
    ) {
        if (!BookingPolicyValidator.requiresQueue(policy, now)) {
            return;
        }
        admissionGuard.ensureAdmitted(policy.performanceId(), input.memberId(), input.admissionToken());
    }
}
