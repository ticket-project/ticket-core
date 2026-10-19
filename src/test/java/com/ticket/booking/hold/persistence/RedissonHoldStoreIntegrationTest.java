package com.ticket.booking.hold.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

import com.ticket.booking.hold.domain.Hold;
import com.ticket.booking.hold.domain.HoldStore;
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
    private static final Long MEMBER_ID = 7L;
    private static final Long OTHER_MEMBER_ID = 8L;
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
                .isEqualTo("7");
    }

    @Test
    void 인덱스가_다른_자료형이면_아무것도_쓰지_않고_실패한다() {
        // 실제 Redis에 다른 자료형을 두어 인덱스 등록이 실패하게 한다. 스크립트는 쓰기 전에 확인하므로 좌석 키도 메타도 남지 않는다.
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
    void 이미_잡힌_좌석과_충돌하면_아무것도_쓰지_않고_남의_선점은_보존한다() {
        final RedissonHoldStore store = new RedissonHoldStore(redissonClient);
        assertThat(store.saveIfAbsent(hold("other-hold", OTHER_MEMBER_ID, List.of(20L)), TTL))
                .isEqualTo(HoldStore.SaveResult.SAVED);

        assertThat(store.saveIfAbsent(hold("new-hold", List.of(10L, 20L)), TTL))
                .isEqualTo(HoldStore.SaveResult.HELD_BY_OTHER);

        assertThat(store.isHeld(PERFORMANCE_ID, 10L)).isFalse();
        assertThat(store.isHeldBy(PERFORMANCE_ID, 20L, "other-hold")).isTrue();
        assertThat(store.getHoldingSeatIds(PERFORMANCE_ID)).containsExactly(20L);
        assertThat(metaExists("new-hold")).isFalse();
        assertThat(metaExists("other-hold")).isTrue();
    }

    private boolean metaExists(final String holdKey) {
        return redissonClient
                        .getBucket(HoldRedisKey.holdMeta(holdKey), StringCodec.INSTANCE)
                        .get()
                != null;
    }

    @Test
    void 같은_회원이_먼저_잡은_좌석과_충돌하면_같은_회원이라고_알리고_아무것도_쓰지_않는다() {
        final RedissonHoldStore store = new RedissonHoldStore(redissonClient);
        assertThat(store.saveIfAbsent(hold("first-click", List.of(20L)), TTL)).isEqualTo(HoldStore.SaveResult.SAVED);

        assertThat(store.saveIfAbsent(hold("second-click", List.of(10L, 20L)), TTL))
                .isEqualTo(HoldStore.SaveResult.HELD_BY_SAME_MEMBER);

        assertThat(store.isHeld(PERFORMANCE_ID, 10L)).isFalse();
        assertThat(store.isHeldBy(PERFORMANCE_ID, 20L, "first-click")).isTrue();
        assertThat(metaExists("second-click")).isFalse();
    }

    private Hold hold(final String holdKey, final List<Long> seatIds) {
        return hold(holdKey, MEMBER_ID, seatIds);
    }

    private Hold hold(final String holdKey, final Long memberId, final List<Long> seatIds) {
        return new Hold(
                holdKey, memberId, PERFORMANCE_ID, seatIds, LocalDateTime.now().plusMinutes(5));
    }
}
