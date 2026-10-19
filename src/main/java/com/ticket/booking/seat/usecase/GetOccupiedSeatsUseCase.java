package com.ticket.booking.seat.usecase;

import static com.ticket.shared.api.InputChecks.requirePositiveId;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

import com.ticket.booking.admission.BookingEntryGuard;
import com.ticket.booking.seat.domain.PerformanceSeatRepository;
import com.ticket.booking.seat.domain.SeatOccupancy;
import com.ticket.show.api.PerformanceSummaryApi;
import com.ticket.show.api.PerformanceSummarySnapshot;

import lombok.RequiredArgsConstructor;

/**
 * 회차에서 빈 좌석이 아닌 좌석 번호만 돌려준다. 좌석 목록은 정적 seat-map에 있으므로 여기 없는 좌석은 AVAILABLE이다.
 *
 * <p>{@link GetSeatStatusUseCase}는 좌석 전체(15,000석이면 약 1MB)를 매번 만들어 보낸다. 이 use case는 점유된 좌석만 읽고 보내서 판매 초반 응답이 거의 비고, 매진
 * 직전에도 좌석 번호 목록 하나다.
 *
 * <p><b>점유 목록은 회차별로 {@link #SNAPSHOT_TTL} 동안 재사용한다.</b> 목록을 만들려면 Redis의 선택·선점 목록을 통째로 읽어야 하는데, 점유가 9,000석쯤 쌓이면 한 번에 약
 * 40KB다. 사용자마다 읽으면 단일 스레드인 Redis가 이 일에 묶여 좌석 락·선점 명령까지 늦어진다(gatling-test capacity-log P-014). 이 목록은 화면 표시용이라 1초 늦어도 된다.
 * 바뀐 좌석은 WebSocket이 따로 알리고, 좌석 선택·선점 판정은 이 목록이 아니라 좌석 하나의 Redis 키를 락 안에서 본다. 예매 진입 검사는 요청마다 한다.
 *
 * <p><b>좌석 화면이 함께 그리는 회차 요약(제목·지역·시작 시각)도 같이 보낸다.</b> 요약을 따로 부르면 예매 사용자마다 HTTP 요청이 하나 더 든다(gatling-test capacity-log
 * 2026-10-19). 요약은 판매 중 바뀌지 않는 표시값이라 점유 목록과 같은 회차별 재사용에 넣는다. 요약 API({@code .../summary})는 그대로 둔다.
 */
@Service
@RequiredArgsConstructor
public class GetOccupiedSeatsUseCase {
    static final Duration SNAPSHOT_TTL = Duration.ofSeconds(1);

    /** 만료된 항목을 이 수를 넘을 때만 치운다. 1초 안에 동시에 보는 회차 수보다 넉넉하면 된다. */
    private static final int SNAPSHOT_SWEEP_THRESHOLD = 64;

    private final PerformanceSeatRepository performanceSeatRepository;
    private final SeatOccupancy seatOccupancy;
    private final BookingEntryGuard bookingEntryGuard;
    private final PerformanceSummaryApi performanceSummaryApi;
    private final Clock clock;
    private final ConcurrentHashMap<Long, Snapshot> snapshots = new ConcurrentHashMap<>();

    public record Input(
            Long performanceId, Long memberId, @Nullable String admissionToken) {
        public Input {
            performanceId = requirePositiveId(performanceId, "performanceId");
            memberId = requirePositiveId(memberId, "memberId");
        }
    }

    /** DB에서 판매 확정된 좌석과 Redis에서 선택·선점 중인 좌석의 합집합, seatId 오름차순. 최대 {@link #SNAPSHOT_TTL} 전 목록일 수 있다. */
    public record Output(List<Long> occupiedSeatIds, PerformanceSummarySnapshot summary) {}

    private record Snapshot(List<Long> seatIds, PerformanceSummarySnapshot summary, long expiresAtMillis) {
        boolean isFresh(final long nowMillis) {
            return nowMillis < expiresAtMillis;
        }
    }

    public Output execute(final Input input) {
        final Long performanceId = input.performanceId();

        bookingEntryGuard.check(performanceId, input.memberId(), input.admissionToken(), LocalDateTime.now(clock));

        final Snapshot snapshot = snapshot(performanceId);
        return new Output(snapshot.seatIds(), snapshot.summary());
    }

    private Snapshot snapshot(final Long performanceId) {
        final Snapshot cached = snapshots.get(performanceId);
        if (cached != null && cached.isFresh(clock.millis())) {
            return cached;
        }
        // 같은 회차를 동시에 놓친 요청은 compute에서 기다렸다가 먼저 읽은 목록을 쓴다. 회차마다 1초에 한 번만 Redis를 읽는다.
        // ponytail: 읽는 동안 같은 해시 칸의 다른 회차도 잠깐 기다린다. 회차가 많이 겹치면 회차별 Future로 바꾼다.
        final Snapshot snapshot = snapshots.compute(
                performanceId,
                (id, current) -> current != null && current.isFresh(clock.millis()) ? current : load(id));
        sweepExpired();
        return snapshot;
    }

    private Snapshot load(final Long performanceId) {
        final TreeSet<Long> occupiedSeatIds =
                new TreeSet<>(performanceSeatRepository.findReservedSeatIds(performanceId));
        occupiedSeatIds.addAll(seatOccupancy.occupiedSeatIds(performanceId));
        return new Snapshot(
                List.copyOf(occupiedSeatIds),
                performanceSummaryApi.getPerformanceSummary(performanceId),
                clock.millis() + SNAPSHOT_TTL.toMillis());
    }

    private void sweepExpired() {
        if (snapshots.size() > SNAPSHOT_SWEEP_THRESHOLD) {
            final long now = clock.millis();
            snapshots.values().removeIf(snapshot -> !snapshot.isFresh(now));
        }
    }
}
