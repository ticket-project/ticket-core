package com.ticket.booking.seat.usecase;

import static com.ticket.shared.api.InputChecks.requirePositiveId;

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
import com.ticket.booking.seat.domain.PerformanceSeat;
import com.ticket.booking.seat.domain.PerformanceSeatRepository;
import com.ticket.booking.seat.domain.PerformanceSeatState;
import com.ticket.booking.selection.domain.SeatSelectionService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GetSeatStatusUseCase {
    private final PerformanceSaleFinder performanceSaleFinder;
    private final PerformanceSeatRepository performanceSeatRepository;
    private final SeatSelectionService seatSelectionService;
    private final HoldManager holdManager;
    private final AdmissionGuard admissionGuard;
    private final Clock clock;

    public record Input(Long performanceId, Long memberId, String admissionToken) {
        public Input {
            performanceId = requirePositiveId(performanceId, "performanceId");
            memberId = requirePositiveId(memberId, "memberId");
        }
    }

    public record Output(List<Seat> seats) {}

    /**
     * 좌석 상태 응답 한 건이다. 기존 프론트가 배치도와 상태를 연결하는 물리 {@code seatId}와 회차 판매 좌석 식별자인 {@code performanceSeatId}를 함께 제공한다.
     * {@code seatId}는 Redis selection/hold 점유 집합(물리 좌석 기준)과 병합하는 join key이기도 하다.
     */
    public record Seat(Long performanceSeatId, Long seatId, SeatStatus status) {}

    /** 좌석 상태 API 응답 전용 enum. DB 저장용 {@link PerformanceSeatState}와 분리해 클라이언트에는 2가지만 노출한다. */
    public enum SeatStatus {
        AVAILABLE("선택 가능"),
        OCCUPIED("사용 중");

        private final String description;

        SeatStatus(final String description) {
            this.description = description;
        }

        public static SeatStatus from(final PerformanceSeatState state) {
            return state == PerformanceSeatState.AVAILABLE ? AVAILABLE : OCCUPIED;
        }

        public String getDescription() {
            return description;
        }
    }

    public Output execute(final Input input) {
        final Long performanceId = input.performanceId();
        final LocalDateTime now = LocalDateTime.now(clock);

        final PerformanceSalesPolicy policy = performanceSaleFinder.requirePolicy(performanceId);
        policy.ensureAcceptingOrders(now);
        admissionGuard.verifyIfRequired(policy, input.performanceId(), input.memberId(), input.admissionToken(), now);

        final List<PerformanceSeat> performanceSeats = performanceSeatRepository.findSeatStates(performanceId);

        final Set<Long> redisOccupiedIds = mergeRedisOccupiedIds(performanceId);

        final List<Seat> seats = performanceSeats.stream()
                .map(performanceSeat -> toSeat(performanceSeat, redisOccupiedIds))
                .toList();

        return new Output(seats);
    }

    /** DB 상태를 응답 상태로 옮기고 Redis 점유를 덧씌운다 — 엔티티의 상태는 바꾸지 않는다. */
    private Seat toSeat(final PerformanceSeat performanceSeat, final Set<Long> redisOccupiedIds) {
        final SeatStatus status = redisOccupiedIds.contains(performanceSeat.getSeatId())
                ? SeatStatus.OCCUPIED
                : SeatStatus.from(performanceSeat.getState());
        return new Seat(performanceSeat.getId(), performanceSeat.getSeatId(), status);
    }

    /**
     * Redis가 점유로 보는 좌석을 합친다 -- 다른 회원이 고르는 중(selection)이거나 이미 선점(hold)한 좌석이다.
     *
     * <p>{@code GetSeatAvailabilityUseCase}에 같은 모양의 method가 있다. "무엇을 점유로 보는가"는 같아야 하므로 모양을 일부러 똑같이 맞춰 둔다 -- 한쪽에 조건이 붙으면
     * 다른 쪽도 함께 본다.
     */
    private Set<Long> mergeRedisOccupiedIds(final Long performanceId) {
        final Set<Long> selectingSeatIds = seatSelectionService.getSelectingSeatIds(performanceId);
        final Set<Long> holdingSeatIds = holdManager.getHoldingSeatIds(performanceId);

        final Set<Long> occupiedSeatIds = HashSet.newHashSet(selectingSeatIds.size() + holdingSeatIds.size());
        occupiedSeatIds.addAll(selectingSeatIds);
        occupiedSeatIds.addAll(holdingSeatIds);
        return occupiedSeatIds;
    }
}
