package com.ticket.core.app.performanceseat.query;

import com.ticket.core.app.error.ApplicationErrorType;
import com.ticket.support.error.CoreException;
import com.ticket.core.domain.performance.repository.PerformanceRepository;
import com.ticket.core.domain.hold.command.HoldManager;
import com.ticket.core.domain.performance.policy.BookingPolicyValidator;
import com.ticket.core.domain.performance.query.model.PerformanceBookingPolicySnapshot;
import com.ticket.core.domain.performanceseat.command.SeatSelectionService;
import com.ticket.core.app.admission.AdmissionGuard;
import com.ticket.core.app.performanceseat.query.model.SeatStateView;
import com.ticket.core.app.performanceseat.query.model.SeatStatus;
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
    private final SeatStatusDbReader seatStatusDbReader;
    private final SeatSelectionService seatSelectionService;
    private final HoldManager holdManager;
    private final AdmissionGuard admissionGuard;
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
                .orElseThrow(() -> new CoreException(ApplicationErrorType.DATA_NOT_FOUND,
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
        admissionGuard.ensureAdmitted(policy.performanceId(), input.memberId(), input.admissionToken());
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
