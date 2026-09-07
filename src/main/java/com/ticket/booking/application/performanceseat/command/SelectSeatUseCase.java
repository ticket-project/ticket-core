package com.ticket.booking.application.performanceseat.command;

import com.ticket.booking.application.admission.AdmissionVerifier;
import com.ticket.booking.application.performanceseat.event.SeatStatusEvent.SeatStatusAction;
import com.ticket.booking.application.performanceseat.event.SeatStatusEventPublisher;
import com.ticket.booking.domain.performancepolicy.model.PerformanceSalesPolicy;
import com.ticket.booking.domain.performancepolicy.repository.PerformanceSalesPolicyRepository;
import com.ticket.booking.domain.performanceseat.support.SeatSelectionAvailabilityValidator;
import com.ticket.error.InvalidRequestException;
import com.ticket.error.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class SelectSeatUseCase {

    private final PerformanceSalesPolicyRepository performanceSalesPolicyRepository;
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

        final PerformanceSalesPolicy policy = findPolicy(input.performanceId());
        policy.ensureAcceptingOrders(now);
        ensureAdmitted(policy, input, now);

        final Long performanceSeatId = seatSelectionAvailabilityValidator.validate(input.performanceId(), input.seatId());

        seatSelectionCoordinator.select(
                input.performanceId(),
                input.seatId(),
                input.memberId(),
                policy.getOrderAcceptanceWindow().getClosesAt()
        );
        seatEventPublisher.publish(input.performanceId(), performanceSeatId, SeatStatusAction.SELECTED);
    }

    private PerformanceSalesPolicy findPolicy(final Long performanceId) {
        return performanceSalesPolicyRepository.findById(performanceId)
                .orElseThrow(() -> new NotFoundException(
                        "회차 판매 정책을 찾을 수 없습니다. id=" + performanceId));
    }

    private void ensureAdmitted(
            final PerformanceSalesPolicy policy,
            final Input input,
            final LocalDateTime now
    ) {
        if (!policy.isQueueRequired(now)) {
            return;
        }
        admissionVerifier.verify(input.performanceId(), input.memberId(), input.admissionToken());
    }
}
