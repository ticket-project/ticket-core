package com.ticket.core.config.cache;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@EnableCaching
public class PerformanceCacheConfiguration {

    private static final Duration CACHE_TTL = Duration.ofSeconds(5);
    private static final long MAXIMUM_CACHE_SIZE = 10_000L;

    @Bean
    public CacheManager cacheManager() {
        final CaffeineCacheManager cacheManager = new CaffeineCacheManager(
                "performanceBookingPolicy",
                "performanceSummary"
        );
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(CACHE_TTL)
                .maximumSize(MAXIMUM_CACHE_SIZE)
                .recordStats());
        return cacheManager;
    }
}
