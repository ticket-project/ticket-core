package com.ticket.booking.application.usecase;

import com.ticket.booking.application.SeatStateSnapshotReader;

import com.ticket.booking.application.AdmissionVerifier;
import com.ticket.booking.domain.HoldManager;
import com.ticket.booking.salespolicy.domain.PerformanceSalesPolicy;
import com.ticket.booking.salespolicy.domain.PerformanceSalesPolicyRepository;
import com.ticket.booking.domain.SeatSelectionService;
import com.ticket.booking.application.SeatStateSnapshotRow;
import com.ticket.booking.application.SeatStateView;
import com.ticket.booking.application.SeatStatus;
import com.ticket.error.InvalidRequestException;
import com.ticket.error.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class GetSeatStatusUseCase {

    private final PerformanceSalesPolicyRepository performanceSalesPolicyRepository;
    private final SeatStateSnapshotReader seatStatusDbReader;
    private final SeatSelectionService seatSelectionService;
    private final HoldManager holdManager;
    private final AdmissionVerifier admissionVerifier;
    private final Clock clock;

    public record Input(Long performanceId, Long memberId, String admissionToken) {
        public Input {
            if (performanceId == null) {
                throw new InvalidRequestException("performanceId는 필수입니다.");
            }
            if (performanceId <= 0) {
                throw new InvalidRequestException("performanceId는 양수여야 합니다.");
            }
            if (memberId == null) {
                throw new InvalidRequestException("memberId는 필수입니다.");
            }
            if (memberId <= 0) {
                throw new InvalidRequestException("memberId는 양수여야 합니다.");
            }
        }
    }

    public record Output(
            List<SeatStateView> seats
    ) {}

    public Output execute(final Input input) {
        final Long performanceId = input.performanceId();
        final LocalDateTime now = LocalDateTime.now(clock);

        final PerformanceSalesPolicy policy = findPolicy(performanceId);
        policy.ensureAcceptingOrders(now);
        ensureAdmitted(policy, input, now);

        final List<SeatStateSnapshotRow> dbStates = seatStatusDbReader.read(performanceId);

        final Set<Long> redisOccupiedIds = mergeRedisOccupiedIds(performanceId);

        final List<SeatStateView> seats = dbStates.stream()
                .map(row -> toSeatStateView(row, redisOccupiedIds))
                .toList();

        return new Output(seats);
    }

    private PerformanceSalesPolicy findPolicy(final Long performanceId) {
        return performanceSalesPolicyRepository.findById(performanceId)
                .orElseThrow(() -> new NotFoundException(
                        "회차 판매 정책을 찾을 수 없습니다. id=" + performanceId));
    }

    private SeatStateView toSeatStateView(final SeatStateSnapshotRow row, final Set<Long> redisOccupiedIds) {
        final SeatStatus status = redisOccupiedIds.contains(row.seatId()) ? SeatStatus.OCCUPIED : row.status();
        return new SeatStateView(row.performanceSeatId(), status);
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

    private Set<Long> mergeRedisOccupiedIds(final Long performanceId) {
        final Set<Long> selectingSeatIds = seatSelectionService.getSelectingSeatIds(performanceId);
        final Set<Long> holdingSeatIds = holdManager.getHoldingSeatIds(performanceId);

        final Set<Long> occupiedSeatIds = HashSet.newHashSet(selectingSeatIds.size() + holdingSeatIds.size());
        occupiedSeatIds.addAll(selectingSeatIds);
        occupiedSeatIds.addAll(holdingSeatIds);
        return occupiedSeatIds;
    }
}
