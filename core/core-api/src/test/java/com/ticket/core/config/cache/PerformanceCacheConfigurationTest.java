package com.ticket.core.config.cache;

import com.github.benmanes.caffeine.cache.Cache;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class PerformanceCacheConfigurationTest {

    @Test
    void configures_bounded_five_second_performance_caches() {
        CacheManager cacheManager = new PerformanceCacheConfiguration().cacheManager();

        assertThat(cacheManager.getCacheNames())
                .containsExactlyInAnyOrder("performanceBookingPolicy", "performanceSummary");

        CaffeineCache springCache = (CaffeineCache) cacheManager.getCache("performanceBookingPolicy");
        Cache<?, ?> nativeCache = springCache.getNativeCache();

        assertThat(nativeCache.policy().expireAfterWrite())
                .hasValueSatisfying(expiration -> assertThat(expiration.getExpiresAfter())
                        .isEqualTo(Duration.ofSeconds(5)));
        assertThat(nativeCache.policy().eviction())
                .hasValueSatisfying(eviction -> assertThat(eviction.getMaximum()).isEqualTo(10_000L));
    }
}
