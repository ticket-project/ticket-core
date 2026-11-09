package com.ticket.booking.seat.usecase;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import com.ticket.booking.seat.domain.PerformanceSeatRepository;
import com.ticket.booking.seat.domain.SeatOccupancy;
import com.ticket.shared.api.CacheNames;
import com.ticket.show.api.PerformanceSummaryApi;
import com.ticket.show.api.PerformanceSummarySnapshot;

import lombok.RequiredArgsConstructor;

/**
 * 회차 좌석을 seatId 오름차순으로 좌석당 1비트(1 = 고를 수 있음)로 묶어 회차별 1초 재사용한다({@link CacheNames#SEAT_AVAILABILITY_BITS}).
 *
 * <p><b>왜 비트인가.</b> {@code .../seats/occupied}는 매진 직전 15,000석이면 좌석 번호 목록이 약 100KB라 요청마다 JSON 변환·gzip에 약 1ms를 썼다. 비트로
 * 보내면 약 2.5KB(base64)다(docs/plans/core-cpu-per-user.md 2절). JSON은 요청마다 만든다.
 *
 * <p><b>왜 별도 bean인가.</b> {@code @Cacheable}은 다른 bean이 부를 때만 걸린다. 트랜잭션 없이 부르므로 {@code sync = true}로 기다리는 요청은 DB
 * connection을 쥐지 않는다. 화면 표시용이라 1초 늦어도 되고, 선택·선점 판정은 이 값을 보지 않는다.
 */
@Component
@RequiredArgsConstructor
public class SeatAvailabilityBitsReader {
    private final PerformanceSeatIdsReader performanceSeatIdsReader;
    private final PerformanceSeatRepository performanceSeatRepository;
    private final SeatOccupancy seatOccupancy;
    private final PerformanceSummaryApi performanceSummaryApi;

    /** {@code available}은 호출한 쪽이 고치지 않는다. */
    @SuppressWarnings("ArrayRecordComponent")
    public record Snapshot(int seatCount, byte[] available, PerformanceSummarySnapshot summary) {}

    @Cacheable(cacheNames = CacheNames.SEAT_AVAILABILITY_BITS, key = "#performanceId", sync = true)
    public Snapshot read(final long performanceId) {
        final List<Long> seatIds = performanceSeatIdsReader.read(performanceId).stream()
                .map(PerformanceSeatIdsReader.SeatIds::seatId)
                .toList();
        final Set<Long> unavailable = new HashSet<>(performanceSeatRepository.findReservedSeatIds(performanceId));
        unavailable.addAll(seatOccupancy.occupiedSeatIds(performanceId));
        return new Snapshot(
                seatIds.size(),
                availableBits(seatIds, unavailable),
                performanceSummaryApi.getPerformanceSummary(performanceId));
    }

    /** 바이트 안에서는 앞 좌석이 높은 비트다. 마지막 바이트의 남는 비트는 0이다. */
    static byte[] availableBits(final List<Long> seatIds, final Set<Long> unavailable) {
        final byte[] bits = new byte[(seatIds.size() + 7) / 8];
        for (int i = 0; i < seatIds.size(); i++) {
            if (!unavailable.contains(seatIds.get(i))) {
                bits[i >>> 3] |= (byte) (0x80 >>> (i & 7));
            }
        }
        return bits;
    }
}
