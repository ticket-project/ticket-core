package com.ticket.venue.usecase;

import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import com.ticket.shared.api.CacheNames;
import com.ticket.venue.api.VenueSeatSnapshot;
import com.ticket.venue.domain.Seat;
import com.ticket.venue.domain.SeatRepository;

import lombok.RequiredArgsConstructor;

/**
 * 공연장의 좌석 전체(주소·좌표)를 좌석 ID로 찾게 담아 읽는다.
 *
 * <p><b>공연장별로 10분 재사용한다({@link CacheNames#VENUE_SEATS}).</b> 좌석 주소는 주문 표시값이고 좌석은 만든 뒤 바뀌지 않는데, 주문마다 좌석 라벨을 DB에서
 * 읽었다(gatling-test capacity-log 2026-11-03). 그 사이 공연장에 더한 좌석은 최대 10분 동안 찾지 못해, 그 좌석의 주문 생성은 실패한다.
 *
 * <p><b>왜 좌석 단위가 아니라 공연장 단위인가.</b> 판매 중 한 회차의 좌석은 대부분 한 번씩만 주문되므로, 좌석 단위로 담으면 거의 다시 쓰이지 않는다. 처음 한 번 공연장 좌석 전체(대형
 * 15,000석)를 읽으면 그 뒤 주문은 모두 캐시에서 고른다.
 *
 * <p><b>적재는 부른 쪽 트랜잭션의 connection으로 한다.</b> {@code sync = true}라 적재하는 동안 같은 공연장을 찾는 요청은 기다린다. 기다리는 요청이 connection을 쥐고
 * 있을 때 적재가 새 connection을 기다리면 서로 막힌다(capacity-log P-022). 그래서 트랜잭션 안({@link SeatLookupService})에서만 부른다.
 *
 * <p><b>왜 별도 bean인가.</b> {@code @Cacheable}은 다른 bean이 부를 때만 걸린다.
 */
@Component
@RequiredArgsConstructor
public class VenueSeatReader {
    private final SeatRepository seatRepository;

    @Cacheable(cacheNames = CacheNames.VENUE_SEATS, key = "#venueId", sync = true)
    public Map<Long, VenueSeatSnapshot> read(final long venueId) {
        return seatRepository.findAllByVenueId(venueId).stream()
                .collect(Collectors.toUnmodifiableMap(Seat::getId, SeatLookupService::toLayout));
    }
}
