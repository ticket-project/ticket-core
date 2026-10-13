package com.ticket.booking.seat.usecase;

import static com.ticket.shared.api.InputChecks.requirePositiveId;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.TreeSet;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

import com.ticket.booking.admission.BookingEntryGuard;
import com.ticket.booking.seat.domain.PerformanceSeatRepository;
import com.ticket.booking.seat.domain.SeatOccupancy;

import lombok.RequiredArgsConstructor;

/**
 * 회차에서 빈 좌석이 아닌 좌석 번호만 돌려준다. 좌석 목록은 정적 seat-map에 있으므로 여기 없는 좌석은 AVAILABLE이다.
 *
 * <p>{@link GetSeatStatusUseCase}는 좌석 전체(15,000석이면 약 1MB)를 매번 만들어 보낸다. 이 use case는 점유된 좌석만 읽고 보내서 판매 초반 응답이 거의 비고, 매진
 * 직전에도 좌석 번호 목록 하나다.
 */
@Service
@RequiredArgsConstructor
public class GetOccupiedSeatsUseCase {
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

    /** DB에서 판매 확정된 좌석과 Redis에서 선택·선점 중인 좌석의 합집합, seatId 오름차순. */
    public record Output(List<Long> occupiedSeatIds) {}

    public Output execute(final Input input) {
        final Long performanceId = input.performanceId();

        bookingEntryGuard.check(performanceId, input.memberId(), input.admissionToken(), LocalDateTime.now(clock));

        final TreeSet<Long> occupiedSeatIds =
                new TreeSet<>(performanceSeatRepository.findReservedSeatIds(performanceId));
        occupiedSeatIds.addAll(seatOccupancy.occupiedSeatIds(performanceId));
        return new Output(List.copyOf(occupiedSeatIds));
    }
}
