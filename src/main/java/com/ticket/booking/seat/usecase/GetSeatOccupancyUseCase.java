package com.ticket.booking.seat.usecase;

import static com.ticket.shared.api.InputChecks.requirePositiveId;

import java.time.Clock;
import java.time.LocalDateTime;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

import com.ticket.booking.admission.BookingEntryGuard;
import com.ticket.show.api.PerformanceSummarySnapshot;

import lombok.RequiredArgsConstructor;

/**
 * 회차 좌석을 고를 수 있는지 좌석당 1비트로 돌려준다. {@link GetOccupiedSeatsUseCase}를 대신할 새 계약이며 옛 API는 전후 측정 뒤 지운다.
 *
 * <p>예매 진입 검사는 요청마다 하고, 비트·좌석 수·회차 요약은 {@link SeatAvailabilityBitsReader}가 회차별 1초 재사용한다. 트랜잭션을 갖지 않는다 — 재사용 값을 기다리는 동안
 * DB connection을 쥐지 않게 한다.
 */
@Service
@RequiredArgsConstructor
public class GetSeatOccupancyUseCase {
    private final SeatAvailabilityBitsReader seatAvailabilityBitsReader;
    private final BookingEntryGuard bookingEntryGuard;
    private final Clock clock;

    public record Input(
            Long performanceId, Long memberId, @Nullable String admissionToken) {
        public Input {
            performanceId = requirePositiveId(performanceId, "performanceId");
            memberId = requirePositiveId(memberId, "memberId");
        }
    }

    /**
     * {@code available}은 seatId 오름차순 좌석마다 1비트(1 = 고를 수 있음)이고 바이트 안에서는 앞 좌석이 높은 비트다. JSON에서는 base64 문자열이다. 받는 쪽은
     * {@code seatCount}로 배치도와 좌석 수가 같은지 확인한다.
     */
    @SuppressWarnings("ArrayRecordComponent")
    public record Output(int seatCount, byte[] available, PerformanceSummarySnapshot summary) {}

    public Output execute(final Input input) {
        bookingEntryGuard.check(
                input.performanceId(), input.memberId(), input.admissionToken(), LocalDateTime.now(clock));

        final SeatAvailabilityBitsReader.Snapshot snapshot = seatAvailabilityBitsReader.read(input.performanceId());
        return new Output(snapshot.seatCount(), snapshot.available(), snapshot.summary());
    }
}
