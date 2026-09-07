package com.ticket.booking.application.performanceseat.command;

import com.ticket.booking.application.support.BookingPolicyGuard;
import com.ticket.booking.application.performanceseat.event.SeatStatusEvent.SeatStatusAction;
import com.ticket.booking.application.performanceseat.event.SeatStatusEventPublisher;
import com.ticket.booking.domain.performanceseat.support.SeatSelectionAvailabilityValidator;
import com.ticket.booking.application.admission.AdmissionVerifier;
import com.ticket.show.BookingPolicyLookup;
import com.ticket.show.BookingPolicySnapshot;
import com.ticket.error.InvalidRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

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
            if (performanceId == null) {
                throw new InvalidRequestException("performanceId는 필수입니다.");
            }
            if (performanceId <= 0) {
                throw new InvalidRequestException("performanceId는 양수여야 합니다.");
            }
            if (seatId == null) {
                throw new InvalidRequestException("seatId는 필수입니다.");
            }
            if (seatId <= 0) {
                throw new InvalidRequestException("seatId는 양수여야 합니다.");
            }
            if (memberId == null) {
                throw new InvalidRequestException("memberId는 필수입니다.");
            }
            if (memberId <= 0) {
                throw new InvalidRequestException("memberId는 양수여야 합니다.");
            }
        }
    }

    public void execute(final Input input) {
        final LocalDateTime now = LocalDateTime.now(clock);

        final BookingPolicySnapshot policy =
                bookingPolicyLookup.getBookingPolicy(input.performanceId());
        BookingPolicyGuard.ensureBookingOpen(policy, now);
        ensureAdmitted(policy, input);

        final Long performanceSeatId = seatSelectionAvailabilityValidator.validate(input.performanceId(), input.seatId());

        seatSelectionCoordinator.select(
                input.performanceId(),
                input.seatId(),
                input.memberId(),
                policy.orderCloseTime()
        );
        seatEventPublisher.publish(input.performanceId(), performanceSeatId, SeatStatusAction.SELECTED);
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
