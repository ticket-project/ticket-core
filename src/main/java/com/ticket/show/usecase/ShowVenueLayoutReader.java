package com.ticket.show.usecase;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import com.ticket.shared.api.CacheNames;

import lombok.RequiredArgsConstructor;

/**
 * 공연장 이름과 좌석 그림 크기를 공연별로 10분 재사용한다({@link CacheNames#SHOW_VENUE_LAYOUT}).
 *
 * <p><b>왜 담는가.</b> 예매 화면에 들어오는 사용자마다 부르는데 요청마다 DB 조회 두 번과 읽기 트랜잭션이 돌았다(docs/plans/core-cpu-per-user.md 4절). 표시값이라 판정과
 * 무관하고, 바뀌면 화면에 최대 10분 옛 값이 보인다.
 *
 * <p><b>캐시를 use case 밖에 둔다.</b> {@code sync = true}로 기다리는 요청이 트랜잭션·DB connection을 쥐지 않게 트랜잭션은 use case 안에서만 연다.
 */
@Component
@RequiredArgsConstructor
public class ShowVenueLayoutReader {
    private final GetShowVenueLayoutUseCase getShowVenueLayoutUseCase;

    @Cacheable(cacheNames = CacheNames.SHOW_VENUE_LAYOUT, key = "#showId", sync = true)
    public GetShowVenueLayoutUseCase.Output read(final long showId) {
        return getShowVenueLayoutUseCase.execute(new GetShowVenueLayoutUseCase.Input(showId));
    }
}
