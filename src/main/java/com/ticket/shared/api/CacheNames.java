package com.ticket.shared.api;

/**
 * 로컬 캐시 이름이다. {@code @Cacheable}을 쓰는 module과 캐시를 등록하는 {@code shared.config.CacheConfig}가 같은 이름을 보도록 한 벌만 둔다.
 *
 * <p>여기 없는 이름을 {@code @Cacheable}에 쓰면 캐시 조회가 실패한다 — CacheConfig가 목록을 고정해 만료·상한 없는 캐시가 조용히 생기지 않게 했다.
 */
public final class CacheNames {
    /** booking 판매 정책. 판정에 쓰지만 판매 중 거의 바뀌지 않아 1초 재사용한다. */
    public static final String SALES_POLICY = "salesPolicy";

    /** show 회차 표시값(공연·회차, 공연장 이름, 등급). 주문 기록에 남기는 이름이라 10분 재사용한다. */
    public static final String PERFORMANCE_DISPLAY = "performanceDisplay";

    private CacheNames() {}
}
