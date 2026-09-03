package com.ticket.booking.internal.application.performanceseat.command;

import com.ticket.booking.internal.application.support.BookingPolicyGuard;
import com.ticket.booking.internal.application.performanceseat.event.SeatStatusEvent.SeatStatusAction;
import com.ticket.booking.internal.application.performanceseat.event.SeatStatusEventPublisher;
import com.ticket.booking.internal.domain.performanceseat.support.SeatSelectionAvailabilityValidator;
import com.ticket.admission.AdmissionVerifier;
import com.ticket.catalog.BookingPolicyLookup;
import com.ticket.catalog.BookingPolicySnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import com.ticket.shared.RequiredInput;

@Service
@RequiredArgsConstructor
public class SelectSeatUseCase {

    private final BookingPolicyLookup bookingPolicyLookup;
    private final SeatSelectionCoordinator seatSelectionCoordinator;
    private final SeatSelectionAvailabilityValidator seatSelectionAvailabilityValidator;
    private final AdmissionVerifier admissionVerifier;
    private final SeatStatusEventPublisher seatEventPublisher;
    private final Clock clock;

    public record Input(Long performanceId, Long seatId, Long memberId, String admissionToken) {
        public Input {
            RequiredInput.positiveId(performanceId, "performanceId");
            RequiredInput.positiveId(seatId, "seatId");
            RequiredInput.positiveId(memberId, "memberId");
        }
    }

    public void execute(final Input input) {
        final LocalDateTime now = LocalDateTime.now(clock);

        final BookingPolicySnapshot policy =
                bookingPolicyLookup.getBookingPolicy(input.performanceId(), List.of());
        BookingPolicyGuard.ensureBookingOpen(policy, now);
        ensureAdmitted(policy, input);

        seatSelectionAvailabilityValidator.validate(input.performanceId(), input.seatId());

        seatSelectionCoordinator.select(
                input.performanceId(),
                input.seatId(),
                input.memberId(),
                policy.orderCloseTime()
        );
        seatEventPublisher.publish(input.performanceId(), input.seatId(), SeatStatusAction.SELECTED);
    }

    private void ensureAdmitted(
            final BookingPolicySnapshot policy,
            final Input input
    ) {
        if (!policy.queueRequired()) {
            return;
        }
        admissionVerifier.verify(policy.performanceId(), input.memberId(), input.admissionToken());
    }
}
