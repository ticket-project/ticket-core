package com.ticket.booking.internal.application.performanceseat.command;

import com.ticket.core.support.exception.ErrorType;
import com.ticket.core.support.exception.CoreException;
import com.ticket.catalog.internal.domain.performance.repository.PerformanceRepository;
import com.ticket.catalog.internal.domain.performance.policy.BookingPolicyValidator;
import com.ticket.catalog.internal.domain.performance.query.PerformanceBookingPolicySnapshot;
import com.ticket.booking.internal.application.performanceseat.event.SeatStatusEvent.SeatStatusAction;
import com.ticket.booking.internal.application.performanceseat.event.SeatStatusEventPublisher;
import com.ticket.booking.internal.domain.performanceseat.support.SeatSelectionAvailabilityValidator;
import com.ticket.admission.AdmissionVerifier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import com.ticket.core.app.support.validation.RequiredInput;

@Service
@RequiredArgsConstructor
public class SelectSeatUseCase {

    private final PerformanceRepository performanceRepository;
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

        final PerformanceBookingPolicySnapshot policy =
                performanceRepository.findBookingPolicyById(input.performanceId())
                        .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND_DATA,
                                "공연을 찾을 수 없습니다. id=" + input.performanceId()));
        BookingPolicyValidator.ensureBookingOpen(policy, now);
        ensureAdmitted(policy, input, now);

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
            final PerformanceBookingPolicySnapshot policy,
            final Input input,
            final LocalDateTime now
    ) {
        if (!BookingPolicyValidator.requiresQueue(policy, now)) {
            return;
        }
        admissionVerifier.verify(policy.performanceId(), input.memberId(), input.admissionToken());
    }
}
