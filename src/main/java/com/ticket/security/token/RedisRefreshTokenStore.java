package com.ticket.security.token;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class RedisRefreshTokenStore implements RefreshTokenStore {
    private static final String KEY_PREFIX = "refresh_token:";
    private final RedissonClient redissonClient;
    private final Supplier<UUID> uuidSupplier;

    @Override
    public String createRefreshToken(final Long memberId, final long expirationSeconds) {
        final String tokenValue = uuidSupplier.get().toString();
        final RBucket<String> bucket = redissonClient.getBucket(KEY_PREFIX + tokenValue);
        bucket.set(String.valueOf(memberId), Duration.ofSeconds(expirationSeconds));
        return tokenValue;
    }

    @Override
    public Optional<Long> consume(final AuthRefreshToken refreshToken) {
        final String memberId = bucketOf(refreshToken).getAndDelete();
        return parseMemberId(memberId);
    }

    @Override
    public Optional<Long> validateWithoutConsume(final AuthRefreshToken refreshToken) {
        return parseMemberId(bucketOf(refreshToken).get());
    }

    @Override
    public boolean revokeIfOwned(final AuthRefreshToken refreshToken, final Long memberId) {
        return bucketOf(refreshToken).compareAndSet(String.valueOf(memberId), null);
    }

    private RBucket<String> bucketOf(final AuthRefreshToken refreshToken) {
        return redissonClient.getBucket(KEY_PREFIX + refreshToken.value());
    }

    private Optional<Long> parseMemberId(final String memberId) {
        if (memberId == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(Long.parseLong(memberId));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
