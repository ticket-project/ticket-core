package com.ticket.booking.seat.usecase;

import java.util.List;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import com.ticket.booking.seat.domain.PerformanceSeatRepository;
import com.ticket.shared.api.CacheNames;

import lombok.RequiredArgsConstructor;

/**
 * 회차에 판매 편성된 좌석의 ID 쌍을 seatId 오름차순으로 회차별 10분 재사용한다({@link CacheNames#PERFORMANCE_SEAT_IDS}).
 *
 * <p><b>왜 담는가.</b> 좌석 상태 응답은 요청마다 회차 좌석 전체(대형 15,000행)를 읽어 객체로 바꿨다. 운영 trace에서 요청 32ms 중 21ms였다(gatling-test
 * capacity-log 2026-11-05). 편성은 판매 중 바뀌지 않고 바뀌는 것은 판매 확정 여부뿐이라, 목록은 여기 담고 판매 확정 좌석은 요청마다 DB에서 읽는다. 그 사이 편성에 더한 좌석은 최대
 * 10분 동안 응답에 없다.
 *
 * <p><b>트랜잭션 밖에서 부른다.</b> 좌석 상태 use case는 트랜잭션을 갖지 않는다(SeatStatusTransactionBoundaryTest). {@code sync = true}로 기다리는
 * 요청은 DB connection을 쥐지 않고, 적재는 조회 한 번을 자동 커밋으로 한다.
 *
 * <p><b>왜 별도 bean인가.</b> {@code @Cacheable}은 다른 bean이 부를 때만 걸린다.
 */
@Component
@RequiredArgsConstructor
public class PerformanceSeatIdsReader {
    private final PerformanceSeatRepository performanceSeatRepository;

    public record SeatIds(Long performanceSeatId, Long seatId) {}

    @Cacheable(cacheNames = CacheNames.PERFORMANCE_SEAT_IDS, key = "#performanceId", sync = true)
    public List<SeatIds> read(final long performanceId) {
        return performanceSeatRepository.findStatesByPerformanceId(performanceId).stream()
                .map(row -> new SeatIds(row.performanceSeatId(), row.seatId()))
                .toList();
    }
}
