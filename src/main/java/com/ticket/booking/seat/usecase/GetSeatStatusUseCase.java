package com.ticket.booking.seat.usecase;

import static com.ticket.shared.api.InputChecks.requirePositiveId;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

import com.ticket.booking.admission.BookingEntryGate;
import com.ticket.booking.seat.domain.PerformanceSeat;
import com.ticket.booking.seat.domain.PerformanceSeatRepository;
import com.ticket.booking.seat.domain.PerformanceSeatState;
import com.ticket.booking.seat.domain.SeatOccupancy;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GetSeatStatusUseCase {
    private final PerformanceSeatRepository performanceSeatRepository;
    private final SeatOccupancy seatOccupancy;
    private final BookingEntryGate bookingEntryGate;
    private final Clock clock;

    public record Input(
            Long performanceId, Long memberId, @Nullable String admissionToken) {
        public Input {
            performanceId = requirePositiveId(performanceId, "performanceId");
            memberId = requirePositiveId(memberId, "memberId");
        }
    }

    public record Output(List<SeatResponse> seats) {}

    /**
     * 좌석 상태 응답 한 건이다. 기존 프론트가 배치도와 상태를 연결하는 물리 {@code seatId}와 회차 판매 좌석 식별자인 {@code performanceSeatId}를 함께 제공한다.
     * {@code seatId}는 Redis selection/hold 점유 집합(물리 좌석 기준)과 병합하는 join key이기도 하다.
     */
    public record SeatResponse(Long performanceSeatId, Long seatId, SeatStatus status) {}

    /** 좌석 상태 API 응답 전용 enum. DB 저장용 {@link PerformanceSeatState}와 분리해 클라이언트에는 2가지만 노출한다. */
    public enum SeatStatus {
        AVAILABLE,
        OCCUPIED;

        public static SeatStatus from(final PerformanceSeatState state) {
            return state == PerformanceSeatState.AVAILABLE ? AVAILABLE : OCCUPIED;
        }
    }

    public Output execute(final Input input) {
        final Long performanceId = input.performanceId();
        final LocalDateTime now = LocalDateTime.now(clock);

        bookingEntryGate.enter(performanceId, input.memberId(), input.admissionToken(), now);

        final List<PerformanceSeat> performanceSeats = performanceSeatRepository.findAllByPerformanceId(performanceId);

        final Set<Long> redisOccupiedIds = seatOccupancy.occupiedSeatIds(performanceId);

        final List<SeatResponse> seats = performanceSeats.stream()
                .map(performanceSeat -> toResponse(performanceSeat, redisOccupiedIds))
                .toList();

        return new Output(seats);
    }

    /** DB 상태를 응답 상태로 옮기고 Redis 점유를 덧씌운다 — 엔티티의 상태는 바꾸지 않는다. */
    private SeatResponse toResponse(final PerformanceSeat performanceSeat, final Set<Long> redisOccupiedIds) {
        final SeatStatus status = redisOccupiedIds.contains(performanceSeat.getSeatId())
                ? SeatStatus.OCCUPIED
                : SeatStatus.from(performanceSeat.getState());
        return new SeatResponse(performanceSeat.getId(), performanceSeat.getSeatId(), status);
    }
}
