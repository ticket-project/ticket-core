package com.ticket.shared.config;

import java.time.Duration;
import java.util.List;

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

    @Bean
    public CacheManager cacheManager() {
        final CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.setCacheNames(List.of(CacheNames.SALES_POLICY, CacheNames.PERFORMANCE_DISPLAY));
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
        return manager;
    }
}
