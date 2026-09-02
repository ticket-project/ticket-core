package com.ticket.booking.internal.application.performanceseat.query;

import com.ticket.core.support.exception.ErrorType;
import com.ticket.core.support.exception.CoreException;
import com.ticket.catalog.internal.domain.performance.repository.PerformanceRepository;
import com.ticket.booking.internal.domain.hold.command.HoldManager;
import com.ticket.catalog.internal.domain.performance.policy.BookingPolicyValidator;
import com.ticket.catalog.internal.domain.performance.query.PerformanceBookingPolicySnapshot;
import com.ticket.booking.internal.domain.performanceseat.command.SeatSelectionService;
import com.ticket.admission.AdmissionVerifier;
import com.ticket.booking.internal.application.performanceseat.query.model.SeatStateView;
import com.ticket.booking.internal.application.performanceseat.query.model.SeatStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import com.ticket.core.app.support.validation.RequiredInput;

@Service
@RequiredArgsConstructor
public class GetSeatStatusUseCase {

    private final PerformanceRepository performanceRepository;
    private final SeatStateSnapshotReader seatStatusDbReader;
    private final SeatSelectionService seatSelectionService;
    private final HoldManager holdManager;
    private final AdmissionVerifier admissionVerifier;
    private final Clock clock;

    public record Input(Long performanceId, Long memberId, String admissionToken) {
        public Input {
            RequiredInput.positiveId(performanceId, "performanceId");
            RequiredInput.positiveId(memberId, "memberId");
        }
    }

    public record Output(
            List<SeatStateView> seats
    ) {}

    public Output execute(final Input input) {
        final Long performanceId = input.performanceId();
        final LocalDateTime now = LocalDateTime.now(clock);

        final PerformanceBookingPolicySnapshot policy = performanceRepository.findBookingPolicyById(performanceId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND_DATA,
                        "공연을 찾을 수 없습니다. id=" + performanceId));
        BookingPolicyValidator.ensureBookingOpen(policy, now);
        ensureAdmitted(policy, input, now);

        final List<SeatStateView> dbStates = seatStatusDbReader.read(performanceId);

        final Set<Long> redisOccupiedIds = mergeRedisOccupiedIds(performanceId);
        if (redisOccupiedIds.isEmpty()) {
            return new Output(dbStates);
        }

        final List<SeatStateView> merged = dbStates.stream()
                .map(seat -> redisOccupiedIds.contains(seat.seatId())
                        ? new SeatStateView(seat.seatId(), SeatStatus.OCCUPIED)
                        : seat)
                .toList();

        return new Output(merged);
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

    private Set<Long> mergeRedisOccupiedIds(final Long performanceId) {
        final Set<Long> selectingSeatIds = seatSelectionService.getSelectingSeatIds(performanceId);
        final Set<Long> holdingSeatIds = holdManager.getHoldingSeatIds(performanceId);

        final Set<Long> occupiedSeatIds = HashSet.newHashSet(selectingSeatIds.size() + holdingSeatIds.size());
        occupiedSeatIds.addAll(selectingSeatIds);
        occupiedSeatIds.addAll(holdingSeatIds);
        return occupiedSeatIds;
    }
}
