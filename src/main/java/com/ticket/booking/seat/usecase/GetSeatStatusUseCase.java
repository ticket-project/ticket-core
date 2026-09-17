package com.ticket.booking.seat.usecase;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.ticket.booking.admission.AdmissionGuard;
import com.ticket.booking.hold.domain.HoldManager;
import com.ticket.booking.salespolicy.domain.PerformanceSalesPolicy;
import com.ticket.booking.salespolicy.usecase.PerformanceSaleFinder;
import com.ticket.booking.seat.query.SeatStateQueryPort;
import com.ticket.booking.seat.query.SeatStateSnapshotRow;
import com.ticket.booking.seat.query.SeatStateView;
import com.ticket.booking.seat.query.SeatStatus;
import com.ticket.booking.selection.domain.SeatSelectionService;
import com.ticket.shared.exception.InvalidRequestException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GetSeatStatusUseCase {
    private final PerformanceSaleFinder performanceSaleFinder;
    private final SeatStateQueryPort seatStateQueryPort;
    private final SeatSelectionService seatSelectionService;
    private final HoldManager holdManager;
    private final AdmissionGuard admissionGuard;
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

    public record Output(List<SeatStateView> seats) {}

    public Output execute(final Input input) {
        final Long performanceId = input.performanceId();
        final LocalDateTime now = LocalDateTime.now(clock);

        final PerformanceSalesPolicy policy = performanceSaleFinder.requirePolicy(performanceId);
        policy.ensureAcceptingOrders(now);
        admissionGuard.verifyIfRequired(
                policy, input.performanceId(), input.memberId(), input.admissionToken(), now);

        final List<SeatStateSnapshotRow> dbStates =
                seatStateQueryPort.findSeatStates(performanceId);

        final Set<Long> redisOccupiedIds = mergeRedisOccupiedIds(performanceId);

        final List<SeatStateView> seats =
                dbStates.stream().map(row -> toSeatStateView(row, redisOccupiedIds)).toList();

        return new Output(seats);
    }

    private SeatStateView toSeatStateView(
            final SeatStateSnapshotRow row, final Set<Long> redisOccupiedIds) {
        final SeatStatus status =
                redisOccupiedIds.contains(row.seatId()) ? SeatStatus.OCCUPIED : row.status();
        return new SeatStateView(row.performanceSeatId(), row.seatId(), status);
    }

    /**
     * Redis가 점유로 보는 좌석을 합친다 -- 다른 회원이 고르는 중(selection)이거나 이미 선점(hold)한 좌석이다.
     *
     * <p>{@code GetSeatAvailabilityUseCase}에 같은 모양의 method가 있다. "무엇을 점유로 보는가"는 같아야 하므로 모양을 일부러 똑같이
     * 맞춰 둔다 -- 한쪽에 조건이 붙으면 다른 쪽도 함께 본다.
     */
    private Set<Long> mergeRedisOccupiedIds(final Long performanceId) {
        final Set<Long> selectingSeatIds = seatSelectionService.getSelectingSeatIds(performanceId);
        final Set<Long> holdingSeatIds = holdManager.getHoldingSeatIds(performanceId);

        final Set<Long> occupiedSeatIds =
                HashSet.newHashSet(selectingSeatIds.size() + holdingSeatIds.size());
        occupiedSeatIds.addAll(selectingSeatIds);
        occupiedSeatIds.addAll(holdingSeatIds);
        return occupiedSeatIds;
    }
}
