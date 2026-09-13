package com.ticket.booking.hold.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.redisson.config.Config;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.ticket.booking.hold.domain.Hold;

/**
 * hold 생성의 부분 실패 보상을 <b>실제 Redis</b>에서 확인한다. mock 테스트는 "어떤 호출을 했는가"만 보므로, 좌석 키·회차별 점유 인덱스·메타데이터가
 * 실제로 어떤 상태로 남는지는 여기서 본다.
 */
@Testcontainers
@SuppressWarnings("NonAsciiCharacters")
class RedissonHoldStoreIntegrationTest {
    private static final int REDIS_PORT = 6379;
    private static final long PERFORMANCE_ID = 9_100L;
    private static final Duration TTL = Duration.ofMinutes(5);

    @Container
    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
                    .withExposedPorts(REDIS_PORT);

    private static RedissonClient redissonClient;

    @BeforeAll
    static void setUpRedisClient() {
        final Config config = new Config();
        config.setCodec(StringCodec.INSTANCE);
        config.useSingleServer()
                .setAddress("redis://" + REDIS.getHost() + ":" + REDIS.getMappedPort(REDIS_PORT));
        redissonClient = Redisson.create(config);
    }

    @AfterAll
    static void closeRedisClient() {
        if (redissonClient != null) {
            redissonClient.shutdown();
        }
    }

    @BeforeEach
    void flushRedis() {
        redissonClient.getKeys().flushall();
    }

    @Test
    void 정상_저장이면_좌석키와_인덱스와_메타가_함께_남는다() {
        final HoldMetaCodec codec = mock(HoldMetaCodec.class);
        when(codec.encode(org.mockito.ArgumentMatchers.any())).thenReturn("{}");
        final RedissonHoldStore store = new RedissonHoldStore(redissonClient, codec);
        final Hold hold = hold("hold-ok", List.of(10L, 20L));

        store.save(hold, TTL);

        assertThat(store.isHeldBy(PERFORMANCE_ID, 10L, "hold-ok")).isTrue();
        assertThat(store.isHeldBy(PERFORMANCE_ID, 20L, "hold-ok")).isTrue();
        assertThat(store.getHoldingSeatIds(PERFORMANCE_ID)).containsExactlyInAnyOrder(10L, 20L);
        assertThat(metaExists("hold-ok")).isTrue();
    }

    /** 메타데이터 기록이 실패하면 앞서 쓴 좌석 키와 인덱스가 모두 사라져야 한다 — 유령 점유가 남으면 그 좌석은 TTL이 끝날 때까지 아무도 살 수 없다. */
    @Test
    void 메타_기록이_실패하면_좌석키와_점유_인덱스가_남지_않는다() {
        final HoldMetaCodec codec = mock(HoldMetaCodec.class);
        when(codec.encode(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new IllegalStateException("hold meta encode failed"));
        final RedissonHoldStore store = new RedissonHoldStore(redissonClient, codec);
        final Hold hold = hold("hold-meta-fail", List.of(10L, 20L));

        assertThatThrownBy(() -> store.save(hold, TTL))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("hold Redis");

        assertThat(store.isHeld(PERFORMANCE_ID, 10L)).isFalse();
        assertThat(store.isHeld(PERFORMANCE_ID, 20L)).isFalse();
        assertThat(store.getHoldingSeatIds(PERFORMANCE_ID)).isEmpty();
        assertThat(metaExists("hold-meta-fail")).isFalse();
    }

    /**
     * 좌석 키를 쓴 직후 실패해도 그 좌석이 정리돼야 한다. 옛 구현은 인덱스 등록까지 끝난 뒤에야 정리 대상으로 기록해서, 좌석 키를 쓰고 인덱스 등록 전에 실패하면 그
     * 좌석 키가 영영 남았다.
     */
    @Test
    void 좌석_하나만_쓰고_실패해도_그_좌석이_남지_않는다() {
        final HoldMetaCodec codec = mock(HoldMetaCodec.class);
        when(codec.encode(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new IllegalStateException("hold meta encode failed"));
        final RedissonHoldStore store = new RedissonHoldStore(redissonClient, codec);

        assertThatThrownBy(() -> store.save(hold("hold-single", List.of(10L)), TTL))
                .isInstanceOf(IllegalStateException.class);

        assertThat(store.isHeld(PERFORMANCE_ID, 10L)).isFalse();
        assertThat(store.getHoldingSeatIds(PERFORMANCE_ID)).isEmpty();
    }

    /**
     * 보상 도중 다른 요청이 같은 좌석을 새로 확보한 상황이다. 소유 키를 확인하지 않으면 보상이 남의 선점을 지운다.
     *
     * <p>메타 인코딩 시점에 좌석 20을 다른 holdKey가 가져가게 해 그 경합을 실제 Redis에서 만든다.
     */
    @Test
    void 보상은_다른_요청이_새로_확보한_선점을_지우지_않는다() {
        final HoldMetaCodec codec = mock(HoldMetaCodec.class);
        final RedissonHoldStore store = new RedissonHoldStore(redissonClient, codec);
        when(codec.encode(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(
                        invocation -> {
                            redissonClient
                                    .getBucket(
                                            HoldRedisKey.hold(PERFORMANCE_ID, 20L),
                                            StringCodec.INSTANCE)
                                    .set("other-hold", TTL);
                            throw new IllegalStateException("hold meta encode failed");
                        });

        assertThatThrownBy(() -> store.save(hold("hold-conflict", List.of(10L, 20L)), TTL))
                .isInstanceOf(IllegalStateException.class);

        assertThat(store.isHeld(PERFORMANCE_ID, 10L)).isFalse();
        assertThat(store.isHeldBy(PERFORMANCE_ID, 20L, "other-hold")).isTrue();
        // 20번은 다른 요청이 점유 중이므로 인덱스에도 그대로 남는다.
        assertThat(store.getHoldingSeatIds(PERFORMANCE_ID)).containsExactly(20L);
    }

    private boolean metaExists(final String holdKey) {
        return redissonClient.getBucket(HoldRedisKey.holdMeta(holdKey), StringCodec.INSTANCE).get()
                != null;
    }

    private Hold hold(final String holdKey, final List<Long> seatIds) {
        return new Hold(holdKey, 7L, PERFORMANCE_ID, seatIds, LocalDateTime.now().plusMinutes(5));
    }
}
