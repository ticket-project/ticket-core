package com.ticket.identity.internal.infrastructure.auth.token;

import com.ticket.identity.internal.application.auth.token.AuthRefreshToken;
import com.ticket.identity.internal.application.auth.token.RefreshTokenStore;
import com.ticket.core.infra.support.UuidSupplier;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class RedisRefreshTokenStore implements RefreshTokenStore {

    private static final String KEY_PREFIX = "refresh_token:";
    private final RedissonClient redissonClient;
    private final UuidSupplier uuidSupplier;

    @Override
    public String createRefreshToken(final Long memberId, final long expirationSeconds) {
        final String tokenValue = uuidSupplier.get().toString();
        final RBucket<String> bucket = redissonClient.getBucket(KEY_PREFIX + tokenValue);
        bucket.set(String.valueOf(memberId), Duration.ofSeconds(expirationSeconds));
        return tokenValue;
    }

    @Override
    public Optional<Long> validate(final AuthRefreshToken refreshToken) {
        final String memberId = bucketOf(refreshToken).getAndDelete();
        return parseMemberId(memberId);
    }

    @Override
    public Optional<Long> validateWithoutConsume(final AuthRefreshToken refreshToken) {
        return parseMemberId(bucketOf(refreshToken).get());
    }

    @Override
    public void revoke(final AuthRefreshToken refreshToken) {
        bucketOf(refreshToken).delete();
    }

    @Override
    public boolean revokeIfOwned(final AuthRefreshToken refreshToken, final Long memberId) {
        return bucketOf(refreshToken).compareAndSet(String.valueOf(memberId), null);
    }

    @Override
    public String rotate(final AuthRefreshToken refreshToken, final Long memberId, final long expirationSeconds) {
        revoke(refreshToken);
        return createRefreshToken(memberId, expirationSeconds);
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
