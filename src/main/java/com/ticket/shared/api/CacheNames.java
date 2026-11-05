package com.ticket.shared.api;

/**
 * 로컬 캐시 이름이다. {@code @Cacheable}을 쓰는 module과 캐시를 등록하는 {@code shared.config.CacheConfig}가 같은 이름을 보도록 한 벌만 둔다.
 *
 * <p>여기 없는 이름을 {@code @Cacheable}에 쓰면 캐시 조회가 실패한다 — CacheConfig가 목록을 고정해 만료·상한 없는 캐시가 조용히 생기지 않게 했다.
 */
public final class CacheNames {
    /** booking 판매 정책. 판정에 쓰지만 판매 중 거의 바뀌지 않아 1초 재사용한다. */
    public static final String SALES_POLICY = "salesPolicy";

    /** show 회차 표시값(공연·회차 시작 시각, 공연장 이름, 등급·가격). 주문 표시값과 등급별 잔여석 조회에 쓰며 판매 중 거의 바뀌지 않아 10분 재사용한다. */
    public static final String PERFORMANCE_DISPLAY = "performanceDisplay";

    /** venue 공연장 좌석 전체(주소·좌표). 주문 표시값의 좌석 라벨에 쓰며 좌석은 만든 뒤 바뀌지 않아 공연장별로 10분 재사용한다. */
    public static final String VENUE_SEATS = "venueSeats";

    /** booking 공연 좌석 배치도 응답 본문(JSON·gzip 바이트, 좌석 상태 없음). 예매 화면에 들어오는 사용자마다 부르며 판매 중 거의 바뀌지 않아 공연별로 10분 재사용한다. */
    public static final String SHOW_SEAT_MAP = "showSeatMap";

    /** booking 회차 판매 편성 좌석 ID 목록(상태 없음). 좌석 상태 응답에 쓰며 편성은 판매 중 바뀌지 않아 회차별로 10분 재사용한다. */
    public static final String PERFORMANCE_SEAT_IDS = "performanceSeatIds";

    private CacheNames() {}
}
