package com.ticket.booking.seat.usecase;

import static com.ticket.shared.api.InputChecks.requirePositiveId;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

import com.ticket.booking.admission.BookingEntryGuard;
import com.ticket.booking.seat.domain.PerformanceSeatRepository;
import com.ticket.booking.seat.domain.SeatOccupancy;

import lombok.RequiredArgsConstructor;

/**
 * 회차 좌석 전체의 현재 상태를 돌려준다. 판매 편성(좌석 ID 목록)은 {@link PerformanceSeatIdsReader}가 회차별로 재사용하고, 요청마다 읽는 것은 판매 확정 좌석(DB 부분 인덱스)과
 * Redis 선택·선점 좌석뿐이다. 응답은 요청 시점 그대로라 재사용으로 늦어지지 않는다.
 *
 * <p>DB 읽기를 Redis 조회 전에 끝내고, 이 use case는 트랜잭션을 갖지 않는다(SeatStatusTransactionBoundaryTest).
 */
@Service
@RequiredArgsConstructor
public class GetSeatStatusUseCase {
    private final PerformanceSeatIdsReader performanceSeatIdsReader;
    private final PerformanceSeatRepository performanceSeatRepository;
    private final SeatOccupancy seatOccupancy;
    private final BookingEntryGuard bookingEntryGuard;
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

    /** 좌석 상태 API 응답 전용 enum. DB 저장 상태(빈 좌석·판매 확정)와 Redis 점유를 합쳐 클라이언트에는 2가지만 노출한다. */
    public enum SeatStatus {
        AVAILABLE,
        OCCUPIED
    }

    public Output execute(final Input input) {
        final Long performanceId = input.performanceId();
        final LocalDateTime now = LocalDateTime.now(clock);

        bookingEntryGuard.check(performanceId, input.memberId(), input.admissionToken(), now);

        final List<PerformanceSeatIdsReader.SeatIds> performanceSeats = performanceSeatIdsReader.read(performanceId);
        final Set<Long> occupiedSeatIds = new HashSet<>(performanceSeatRepository.findReservedSeatIds(performanceId));

        occupiedSeatIds.addAll(seatOccupancy.occupiedSeatIds(performanceId));

        final List<SeatResponse> seats = performanceSeats.stream()
                .map(seat -> new SeatResponse(
                        seat.performanceSeatId(),
                        seat.seatId(),
                        occupiedSeatIds.contains(seat.seatId()) ? SeatStatus.OCCUPIED : SeatStatus.AVAILABLE))
                .toList();

        return new Output(seats);
    }
}
