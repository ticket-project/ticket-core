package com.ticket.core.infra.auth.oauth2;

import com.ticket.core.domain.auth.oauth2.OAuth2AuthCodeStore;
import com.ticket.core.infra.support.UuidSupplier;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class RedisOAuth2AuthCodeStore implements OAuth2AuthCodeStore {

    private static final String KEY_PREFIX = "oauth2_auth_code:";
    private static final Duration CODE_TTL = Duration.ofSeconds(30);

    private final RedissonClient redissonClient;
    private final UuidSupplier uuidSupplier;

    @Override
    public String createCode(final Long memberId) {
        final String code = uuidSupplier.get().toString();
        final RBucket<String> bucket = redissonClient.getBucket(KEY_PREFIX + code);
        bucket.set(String.valueOf(memberId), CODE_TTL);
        return code;
    }

    @Override
    public Optional<Long> consumeCode(final String code) {
        final RBucket<String> bucket = redissonClient.getBucket(KEY_PREFIX + code);
        final String memberId = bucket.getAndDelete();
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
