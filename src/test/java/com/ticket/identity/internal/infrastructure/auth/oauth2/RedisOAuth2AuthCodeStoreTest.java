package com.ticket.identity.internal.infrastructure.auth.oauth2;

import com.ticket.core.infra.support.UuidSupplier;
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
class RedisOAuth2AuthCodeStoreTest {

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RBucket<String> bucket;

    @Mock
    private UuidSupplier uuidSupplier;

    @InjectMocks
    private RedisOAuth2AuthCodeStore oauth2AuthCodeStore;

    @Test
    void creates_one_time_auth_code_and_stores_member_id() {
        doReturn(bucket).when(redissonClient).getBucket(anyString());
        when(uuidSupplier.get()).thenReturn(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"));

        String code = oauth2AuthCodeStore.createCode(7L);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(redissonClient).getBucket(keyCaptor.capture());
        verify(bucket).set("7", Duration.ofSeconds(30));
        assertThat(keyCaptor.getValue()).isEqualTo("oauth2_auth_code:123e4567-e89b-12d3-a456-426614174000");
        assertThat(code).isEqualTo("123e4567-e89b-12d3-a456-426614174000");
    }

    @Test
    void consume_code_deletes_code_and_returns_member_id() {
        doReturn(bucket).when(redissonClient).getBucket("oauth2_auth_code:code");
        when(bucket.getAndDelete()).thenReturn("7");

        assertThat(oauth2AuthCodeStore.consumeCode("code")).contains(7L);
    }

    @Test
    void consume_code_returns_empty_when_value_is_missing_or_not_number() {
        doReturn(bucket).when(redissonClient).getBucket("oauth2_auth_code:code");
        when(bucket.getAndDelete()).thenReturn("not-a-number");

        assertThat(oauth2AuthCodeStore.consumeCode("code")).isEmpty();
    }
}
