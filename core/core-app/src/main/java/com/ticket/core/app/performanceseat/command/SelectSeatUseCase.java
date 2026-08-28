package com.ticket.core.app.performanceseat.command;

import com.ticket.core.app.error.ApplicationErrorType;
import com.ticket.support.error.CoreException;
import com.ticket.core.domain.performance.repository.PerformanceRepository;
import com.ticket.core.domain.performanceseat.support.SeatStatusMessage;
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
import com.ticket.core.app.support.validation.RequiredInput;

@Service
@RequiredArgsConstructor
public class SelectSeatUseCase {

    private final PerformanceRepository performanceRepository;
    private final SeatSelectionCoordinator seatSelectionCoordinator;
    private final SeatSelectionAvailabilityValidator seatSelectionAvailabilityValidator;
    private final AdmissionGuard admissionGuard;
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

        final PerformanceBookingPolicyView policy =
                performanceRepository.findBookingPolicyById(input.performanceId())
                        .orElseThrow(() -> new CoreException(ApplicationErrorType.DATA_NOT_FOUND,
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
