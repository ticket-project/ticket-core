package com.ticket.booking.hold.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.Redisson;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.redisson.config.Config;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.ticket.booking.hold.domain.Hold;
import com.ticket.testsupport.TestContainerImages;

/**
 * hold 생성의 부분 실패 보상을 <b>실제 Redis</b>에서 확인한다. mock 테스트는 "어떤 호출을 했는가"만 보므로, 좌석 키·회차별 점유 인덱스·메타데이터가 실제로 어떤 상태로 남는지는 여기서
 * 본다.
 */
@Testcontainers
@SuppressWarnings("NonAsciiCharacters")
class RedissonHoldStoreIntegrationTest {
    private static final int REDIS_PORT = 6379;
    private static final long PERFORMANCE_ID = 9_100L;
    private static final Duration TTL = Duration.ofMinutes(5);

    @Container
    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(TestContainerImages.REDIS).withExposedPorts(REDIS_PORT);

    private static RedissonClient redissonClient;

    @BeforeAll
    static void setUpRedisClient() {
        final Config config = new Config();
        config.setCodec(StringCodec.INSTANCE);
        config.useSingleServer().setAddress("redis://" + REDIS.getHost() + ":" + REDIS.getMappedPort(REDIS_PORT));
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
    void 정상_저장이면_좌석키와_인덱스와_holdKey_메타가_함께_남는다() {
        final RedissonHoldStore store = new RedissonHoldStore(redissonClient);
        final Hold hold = hold("hold-ok", List.of(10L, 20L));

        store.saveIfAbsent(hold, TTL);

        assertThat(store.isHeldBy(PERFORMANCE_ID, 10L, "hold-ok")).isTrue();
        assertThat(store.isHeldBy(PERFORMANCE_ID, 20L, "hold-ok")).isTrue();
        assertThat(store.getHoldingSeatIds(PERFORMANCE_ID)).containsExactlyInAnyOrder(10L, 20L);
        assertThat(redissonClient
                        .getBucket(HoldRedisKey.holdMeta("hold-ok"), StringCodec.INSTANCE)
                        .get())
                .isEqualTo("hold-ok");
    }

    @Test
    void 메타_기록이_실패하면_좌석키와_점유_인덱스가_남지_않는다() {
        final RBucket<Object> meta =
                spy(redissonClient.getBucket(HoldRedisKey.holdMeta("hold-meta-fail"), StringCodec.INSTANCE));
        doThrow(new IllegalStateException("hold meta write failed")).when(meta).set("hold-meta-fail", TTL);
        final RedissonHoldStore store = storeWithMeta("hold-meta-fail", meta);

        assertThatThrownBy(() -> store.saveIfAbsent(hold("hold-meta-fail", List.of(10L, 20L)), TTL))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("hold Redis");

        assertThat(store.isHeld(PERFORMANCE_ID, 10L)).isFalse();
        assertThat(store.isHeld(PERFORMANCE_ID, 20L)).isFalse();
        assertThat(store.getHoldingSeatIds(PERFORMANCE_ID)).isEmpty();
        assertThat(metaExists("hold-meta-fail")).isFalse();
    }

    @Test
    void 인덱스_등록이_실패해도_직전에_쓴_좌석이_남지_않는다() {
        // 실제 Redis에 다른 자료형을 두어 좌석 키 쓰기 직후 인덱스 등록이 실패하도록 한다.
        redissonClient
                .getBucket(HoldRedisKey.holdSeatIndex(PERFORMANCE_ID), StringCodec.INSTANCE)
                .set("wrong-type", TTL);
        final RedissonHoldStore store = new RedissonHoldStore(redissonClient);

        assertThatThrownBy(() -> store.saveIfAbsent(hold("hold-single", List.of(10L)), TTL))
                .isInstanceOf(IllegalStateException.class);

        assertThat(store.isHeld(PERFORMANCE_ID, 10L)).isFalse();
        assertThat(metaExists("hold-single")).isFalse();
    }

    @Test
    void 보상은_다른_요청이_새로_확보한_선점을_지우지_않는다() {
        final RBucket<Object> meta =
                spy(redissonClient.getBucket(HoldRedisKey.holdMeta("hold-conflict"), StringCodec.INSTANCE));
        doAnswer(invocation -> {
                    redissonClient
                            .getBucket(HoldRedisKey.hold(PERFORMANCE_ID, 20L), StringCodec.INSTANCE)
                            .set("other-hold", TTL);
                    throw new IllegalStateException("hold meta write failed");
                })
                .when(meta)
                .set("hold-conflict", TTL);
        final RedissonHoldStore store = storeWithMeta("hold-conflict", meta);

        assertThatThrownBy(() -> store.saveIfAbsent(hold("hold-conflict", List.of(10L, 20L)), TTL))
                .isInstanceOf(IllegalStateException.class);

        assertThat(store.isHeld(PERFORMANCE_ID, 10L)).isFalse();
        assertThat(store.isHeldBy(PERFORMANCE_ID, 20L, "other-hold")).isTrue();
        assertThat(store.getHoldingSeatIds(PERFORMANCE_ID)).containsExactly(20L);
    }

    @Test
    void 기존_JSON_메타를_읽지_않고_남의_좌석을_보존하며_반복_해제한다() {
        final RedissonHoldStore store = new RedissonHoldStore(redissonClient);
        store.saveIfAbsent(hold("hold-legacy", List.of(10L, 20L)), TTL);
        redissonClient
                .getBucket(HoldRedisKey.holdMeta("hold-legacy"), StringCodec.INSTANCE)
                .set("{legacy-json}", TTL);
        redissonClient
                .getBucket(HoldRedisKey.hold(PERFORMANCE_ID, 20L), StringCodec.INSTANCE)
                .set("other-hold", TTL);

        store.release(PERFORMANCE_ID, "hold-legacy", List.of(20L, 10L, 10L));
        store.release(PERFORMANCE_ID, "hold-legacy", List.of(10L, 20L));

        assertThat(store.isHeld(PERFORMANCE_ID, 10L)).isFalse();

        assertThat(metaExists("hold-legacy")).isFalse();
        assertThat(store.isHeldBy(PERFORMANCE_ID, 20L, "other-hold")).isTrue();
        assertThat(store.getHoldingSeatIds(PERFORMANCE_ID)).containsExactly(20L);
    }

    @Test
    void 이미_잡힌_좌석과_충돌하면_남의_선점은_보존하고_앞서_쓴_좌석은_보상한다() {
        final RedissonHoldStore store = new RedissonHoldStore(redissonClient);
        assertThat(store.saveIfAbsent(hold("other-hold", List.of(20L)), TTL)).isTrue();

        assertThat(store.saveIfAbsent(hold("new-hold", List.of(10L, 20L)), TTL)).isFalse();

        assertThat(store.isHeld(PERFORMANCE_ID, 10L)).isFalse();
        assertThat(store.isHeldBy(PERFORMANCE_ID, 20L, "other-hold")).isTrue();
        assertThat(store.getHoldingSeatIds(PERFORMANCE_ID)).containsExactly(20L);
        assertThat(metaExists("new-hold")).isFalse();
        assertThat(metaExists("other-hold")).isTrue();
    }

    private RedissonHoldStore storeWithMeta(final String holdKey, final RBucket<Object> meta) {
        final RedissonClient client = spy(redissonClient);
        doReturn(meta).when(client).getBucket(HoldRedisKey.holdMeta(holdKey), StringCodec.INSTANCE);
        return new RedissonHoldStore(client);
    }

    private boolean metaExists(final String holdKey) {
        return redissonClient
                        .getBucket(HoldRedisKey.holdMeta(holdKey), StringCodec.INSTANCE)
                        .get()
                != null;
    }

    private Hold hold(final String holdKey, final List<Long> seatIds) {
        return new Hold(
                holdKey, 7L, PERFORMANCE_ID, seatIds, LocalDateTime.now().plusMinutes(5));
    }
}
