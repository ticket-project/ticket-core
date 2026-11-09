package com.ticket.shared.config;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.ticket.shared.api.CacheNames;

/**
 * 로컬 캐시(Caffeine)를 켜고 캐시마다 유지 시간·상한을 정한다.
 *
 * <p><b>CacheManager를 직접 만든다.</b> Spring Boot의 자동 선택은 Redis를 Caffeine보다 먼저 고르는데, Redisson이 Spring Data Redis를 함께 가져오므로
 * 그대로 두면 캐시가 Redis로 간다. 목적이 Core CPU를 줄이는 것이라(gatling-test capacity-log 2026-10-22) Redis 왕복으로 바꾸면 효과가 없다.
 *
 * <p><b>{@code setCacheNames} 뒤에 {@code registerCustomCache}를 부른다.</b> {@code setCacheNames}는 이름마다 만료·상한 없는 기본 캐시를 만들고
 * 목록 밖 이름을 막는다. 순서를 바꾸면 기본 캐시가 아래 설정을 덮어쓴다.
 *
 * <p>판정 경로의 좌석 상태·회원·진행 중 주문은 캐시하지 않는다. 결제가 붙으면 좌석 상태가 바뀌어 이중 판매가 된다.
 */
@Configuration(proxyBeanMethods = false)
@EnableCaching
public class CacheConfig {
    static final Duration SALES_POLICY_TTL = Duration.ofSeconds(1);
    static final Duration PERFORMANCE_DISPLAY_TTL = Duration.ofMinutes(10);
    static final long MAXIMUM_SIZE = 1_000;
    static final Duration VENUE_SEATS_TTL = Duration.ofMinutes(10);
    /** 공연장 하나가 좌석 수만큼 무게를 갖는다. 15,000석 공연장 4개를 담는다. */
    static final long VENUE_SEATS_MAXIMUM_SEATS = 60_000;

    static final Duration SHOW_SEAT_MAP_TTL = Duration.ofMinutes(10);
    /** 15,000석 배치도 하나가 JSON·gzip 합쳐 약 2.4MB다. 동시에 판매하는 인기 공연 수만큼만 담는다. */
    static final long SHOW_SEAT_MAP_MAXIMUM_SIZE = 10;

    static final Duration PERFORMANCE_SEAT_IDS_TTL = Duration.ofMinutes(10);
    /** 회차 하나가 좌석 수만큼 무게를 갖는다. 15,000석 회차 10개를 담는다. */
    static final long PERFORMANCE_SEAT_IDS_MAXIMUM_SEATS = 150_000;

    static final Duration SEAT_AVAILABILITY_BITS_TTL = Duration.ofSeconds(1);
    /** 15,000석 회차 하나가 약 2KB다. 1초 안에 동시에 보는 회차 수보다 넉넉하면 된다. */
    static final long SEAT_AVAILABILITY_BITS_MAXIMUM_SIZE = 64;

    static final Duration SHOW_VENUE_LAYOUT_TTL = Duration.ofMinutes(10);
    /** 값 하나가 이름과 숫자 셋이라 작다. 동시에 판매하는 공연 수보다 넉넉하면 된다. */
    static final long SHOW_VENUE_LAYOUT_MAXIMUM_SIZE = 100;

    @Bean
    public CacheManager cacheManager() {
        final CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.setCacheNames(List.of(
                CacheNames.SALES_POLICY,
                CacheNames.PERFORMANCE_DISPLAY,
                CacheNames.VENUE_SEATS,
                CacheNames.SHOW_SEAT_MAP,
                CacheNames.PERFORMANCE_SEAT_IDS,
                CacheNames.SEAT_AVAILABILITY_BITS,
                CacheNames.SHOW_VENUE_LAYOUT));
        // 판매 기간·대기열 변경은 서버마다 최대 1초 늦게 반영된다.
        manager.registerCustomCache(
                CacheNames.SALES_POLICY,
                Caffeine.newBuilder()
                        .expireAfterWrite(SALES_POLICY_TTL)
                        .maximumSize(MAXIMUM_SIZE)
                        .recordStats()
                        .build());
        // 공연명·공연장 이름·회차 시작 시각·등급 표시값이 바뀌면 주문 표시값과 등급별 잔여석 조회에 최대 10분 옛 값이 남는다.
        // 주문 금액은 좌석 단가라 영향이 없다.
        manager.registerCustomCache(
                CacheNames.PERFORMANCE_DISPLAY,
                Caffeine.newBuilder()
                        .expireAfterWrite(PERFORMANCE_DISPLAY_TTL)
                        .maximumSize(MAXIMUM_SIZE)
                        .recordStats()
                        .build());
        // 공연장 좌석 수가 크게 달라 개수가 아니라 좌석 수로 상한을 둔다. 좌석을 더하면 그 좌석의 주문은 최대 10분 실패한다.
        manager.registerCustomCache(
                CacheNames.VENUE_SEATS,
                Caffeine.newBuilder()
                        .expireAfterWrite(VENUE_SEATS_TTL)
                        .maximumWeight(VENUE_SEATS_MAXIMUM_SEATS)
                        .weigher((final Object venueId, final Object seats) -> ((Map<?, ?>) seats).size())
                        .recordStats()
                        .build());
        // 좌석 배치도의 등급 이름·가격·좌표가 바뀌면 화면에 최대 10분 옛 값이 보인다. 주문 금액은 주문 때 좌석 단가로 정해 영향이 없다.
        manager.registerCustomCache(
                CacheNames.SHOW_SEAT_MAP,
                Caffeine.newBuilder()
                        .expireAfterWrite(SHOW_SEAT_MAP_TTL)
                        .maximumSize(SHOW_SEAT_MAP_MAXIMUM_SIZE)
                        .recordStats()
                        .build());
        // 판매 편성에 좌석을 더하면 그 좌석은 좌석 상태 응답에 최대 10분 없다. 상태(판매 확정·점유)는 요청마다 읽는다.
        manager.registerCustomCache(
                CacheNames.PERFORMANCE_SEAT_IDS,
                Caffeine.newBuilder()
                        .expireAfterWrite(PERFORMANCE_SEAT_IDS_TTL)
                        .maximumWeight(PERFORMANCE_SEAT_IDS_MAXIMUM_SEATS)
                        .weigher((final Object performanceId, final Object seats) -> ((List<?>) seats).size())
                        .recordStats()
                        .build());
        // 좌석 화면의 고를 수 있는 좌석 표시가 최대 1초 늦다. 바뀐 좌석은 WebSocket이 따로 알리고 판정은 이 값을 보지 않는다.
        manager.registerCustomCache(
                CacheNames.SEAT_AVAILABILITY_BITS,
                Caffeine.newBuilder()
                        .expireAfterWrite(SEAT_AVAILABILITY_BITS_TTL)
                        .maximumSize(SEAT_AVAILABILITY_BITS_MAXIMUM_SIZE)
                        .recordStats()
                        .build());
        // 공연장 이름·좌석 그림 크기가 바뀌면 화면에 최대 10분 옛 값이 보인다. 판정에는 쓰지 않는다.
        manager.registerCustomCache(
                CacheNames.SHOW_VENUE_LAYOUT,
                Caffeine.newBuilder()
                        .expireAfterWrite(SHOW_VENUE_LAYOUT_TTL)
                        .maximumSize(SHOW_VENUE_LAYOUT_MAXIMUM_SIZE)
                        .recordStats()
                        .build());
        return manager;
    }
}
