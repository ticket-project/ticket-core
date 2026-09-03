package com.ticket.identity.internal.infrastructure.auth.token;

import com.ticket.identity.internal.application.auth.token.AuthRefreshToken;
import com.ticket.shared.UuidSupplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisRefreshTokenStoreTest {

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RBucket<String> bucket;

    @Mock
    private UuidSupplier uuidSupplier;

    @InjectMocks
    private RedisRefreshTokenStore refreshTokenStore;

    @Test
    void creates_refresh_token_and_stores_member_id() {
        doReturn(bucket).when(redissonClient).getBucket(anyString());
        when(uuidSupplier.get()).thenReturn(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"));

        String token = refreshTokenStore.createRefreshToken(3L, 120L);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(redissonClient).getBucket(keyCaptor.capture());
        verify(bucket).set("3", Duration.ofSeconds(120L));
        assertThat(keyCaptor.getValue()).isEqualTo("refresh_token:123e4567-e89b-12d3-a456-426614174000");
        assertThat(token).isEqualTo("123e4567-e89b-12d3-a456-426614174000");
    }

    @Test
    void validate_consumes_token_and_returns_member_id() {
        doReturn(bucket).when(redissonClient).getBucket("refresh_token:token-value");
        when(bucket.getAndDelete()).thenReturn("3");

        assertThat(refreshTokenStore.validate(AuthRefreshToken.from("token-value"))).contains(3L);
    }

    @Test
    void validate_without_consume_returns_member_id() {
        doReturn(bucket).when(redissonClient).getBucket("refresh_token:token-value");
        when(bucket.get()).thenReturn("3");

        assertThat(refreshTokenStore.validateWithoutConsume(AuthRefreshToken.from("token-value"))).contains(3L);
    }

    @Test
    void validate_returns_empty_when_stored_member_id_is_not_number() {
        doReturn(bucket).when(redissonClient).getBucket("refresh_token:token-value");
        when(bucket.getAndDelete()).thenReturn("not-a-number");

        assertThat(refreshTokenStore.validate(AuthRefreshToken.from("token-value"))).isEmpty();
    }

    @Test
    void revoke_if_owned_deletes_only_matching_owner_token() {
        doReturn(bucket).when(redissonClient).getBucket("refresh_token:token-value");
        when(bucket.compareAndSet("3", null)).thenReturn(true);

        boolean revoked = refreshTokenStore.revokeIfOwned(AuthRefreshToken.from("token-value"), 3L);

        assertThat(revoked).isTrue();
        verify(bucket).compareAndSet("3", null);
    }
}
