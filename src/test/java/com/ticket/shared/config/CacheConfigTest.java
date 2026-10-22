package com.ticket.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Objects;

import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;

import com.github.benmanes.caffeine.cache.Policy;
import com.ticket.shared.api.CacheNames;

/** 캐시별 설정이 실제로 걸렸는지 본다. setCacheNames와 registerCustomCache의 순서가 바뀌면 만료·상한 없는 기본 캐시가 남아 여기서 실패한다. */
@SuppressWarnings("NonAsciiCharacters")
class CacheConfigTest {
    private final CacheManager cacheManager = new CacheConfig().cacheManager();

    @Test
    void 판매_정책은_1초_뒤_만료되고_개수_상한이_있다() {
        final Policy<Object, Object> policy = policyOf(CacheNames.SALES_POLICY);

        assertThat(policy.expireAfterWrite().orElseThrow().getExpiresAfter()).isEqualTo(Duration.ofSeconds(1));
        assertThat(policy.eviction().orElseThrow().getMaximum()).isEqualTo(1_000);
    }

    @Test
    void 회차_표시값은_10분_뒤_만료되고_개수_상한이_있다() {
        final Policy<Object, Object> policy = policyOf(CacheNames.PERFORMANCE_DISPLAY);

        assertThat(policy.expireAfterWrite().orElseThrow().getExpiresAfter()).isEqualTo(Duration.ofMinutes(10));
        assertThat(policy.eviction().orElseThrow().getMaximum()).isEqualTo(1_000);
    }

    @Test
    void 등록하지_않은_이름으로는_캐시를_만들지_않는다() {
        assertThat(cacheManager.getCache("unknown")).isNull();
    }

    private Policy<Object, Object> policyOf(final String name) {
        final CaffeineCache cache = (CaffeineCache) Objects.requireNonNull(cacheManager.getCache(name));
        return cache.getNativeCache().policy();
    }
}
