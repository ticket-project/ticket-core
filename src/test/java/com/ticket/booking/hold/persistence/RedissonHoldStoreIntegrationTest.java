package com.ticket.booking.hold.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.mock;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.Redisson;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.redisson.config.Config;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.ticket.booking.hold.domain.Hold;
import com.ticket.booking.hold.domain.HoldStore;
import com.ticket.booking.seat.port.SeatStatusEvent;
import com.ticket.booking.seat.port.SeatStatusEvent.SeatStatusAction;
import com.ticket.booking.selection.persistence.SeatSelectionRedisKey;
import com.ticket.booking.websocket.SeatStatusRelay;
import com.ticket.testsupport.TestContainerImages;

import tools.jackson.databind.json.JsonMapper;

/**
 * hold 저장·확정·해제가 <b>실제 Redis</b>에 남기는 상태와 relay 발행을 확인한다. 좌석 락이 없으므로, 확인과 쓰기·발행이 스크립트 하나라 선택과 선점이 엇갈리지 않는 것과 남이 잡은 좌석을
 * 건드리거나 비었다고 알리지 않는 것을 여기서 고정한다.
 */
@Testcontainers
@SuppressWarnings("NonAsciiCharacters")
class RedissonHoldStoreIntegrationTest {
    private static final int REDIS_PORT = 6379;
    private static final long PERFORMANCE_ID = 9_100L;
    private static final Long MEMBER_ID = 7L;
    private static final Long OTHER_MEMBER_ID = 8L;
    private static final Duration TTL = Duration.ofMinutes(5);
    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 3, 15, 10, 0);

    @Container
    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(TestContainerImages.REDIS).withExposedPorts(REDIS_PORT);

    private static RedissonClient redissonClient;
    private static RTopic relayTopic;
    private static final List<SeatStatusEvent> RELAYED = new CopyOnWriteArrayList<>();

    private final RedissonHoldStore store =
            new RedissonHoldStore(redissonClient, new SeatStatusRelay(mock(SimpMessagingTemplate.class), JSON_MAPPER));

    @BeforeAll
    static void setUpRedisClient() {
        final Config config = new Config();
        config.setCodec(StringCodec.INSTANCE);
        config.useSingleServer().setAddress("redis://" + REDIS.getHost() + ":" + REDIS.getMappedPort(REDIS_PORT));
        redissonClient = Redisson.create(config);
        relayTopic = redissonClient.getTopic(SeatStatusRelay.CHANNEL, StringCodec.INSTANCE);
        relayTopic.addListener(
                String.class, (channel, message) -> RELAYED.add(JSON_MAPPER.readValue(message, SeatStatusEvent.class)));
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
        RELAYED.clear();
    }

    @Test
    void 본인이_선택한_좌석이면_좌석키와_인덱스와_회원ID_메타가_함께_남는다() {
        select(MEMBER_ID, 10L, 20L);

        assertThat(store.saveIfAbsent(hold("hold-ok", List.of(10L, 20L)), TTL)).isEqualTo(HoldStore.SaveResult.SAVED);

        assertThat(holder(10L)).isEqualTo("hold-ok");
        assertThat(holder(20L)).isEqualTo("hold-ok");
        assertThat(store.getHoldingSeatIds(PERFORMANCE_ID)).containsExactlyInAnyOrder(10L, 20L);
        assertThat(redissonClient
                        .getBucket(HoldRedisKey.holdMeta("hold-ok"), StringCodec.INSTANCE)
                        .get())
                .isEqualTo("7");
        assertNothingRelayedAfterMarker();
    }

    /** 확인과 선점이 한 스크립트라, 그 사이 선택이 풀리고 남이 다시 고른 좌석을 선점하지 못한다(ADR 0021). */
    @Test
    void 본인이_선택_중이_아닌_좌석이_있으면_아무것도_쓰지_않는다() {
        select(MEMBER_ID, 10L);
        select(OTHER_MEMBER_ID, 20L);

        assertThat(store.saveIfAbsent(hold("hold-mixed", List.of(10L, 20L)), TTL))
                .isEqualTo(HoldStore.SaveResult.NOT_SELECTED);

        assertThat(holder(10L)).isNull();
        assertThat(holder(20L)).isNull();
        assertThat(metaExists("hold-mixed")).isFalse();
    }

    @Test
    void 인덱스가_다른_자료형이면_아무것도_쓰지_않고_실패한다() {
        // 실제 Redis에 다른 자료형을 두어 인덱스 등록이 실패하게 한다. 스크립트는 쓰기 전에 확인하므로 좌석 키도 메타도 남지 않는다.
        redissonClient
                .getBucket(HoldRedisKey.holdSeatIndex(PERFORMANCE_ID), StringCodec.INSTANCE)
                .set("wrong-type", TTL);
        select(MEMBER_ID, 10L);

        assertThatThrownBy(() -> store.saveIfAbsent(hold("hold-single", List.of(10L)), TTL))
                .isInstanceOf(IllegalStateException.class);

        assertThat(holder(10L)).isNull();
        assertThat(metaExists("hold-single")).isFalse();
    }

    @Test
    void 이미_잡힌_좌석과_충돌하면_아무것도_쓰지_않고_남의_선점은_보존한다() {
        select(MEMBER_ID, 10L, 20L);
        setHolder(20L, "other-hold");
        redissonClient
                .getBucket(HoldRedisKey.holdMeta("other-hold"), StringCodec.INSTANCE)
                .set(OTHER_MEMBER_ID.toString(), TTL);

        assertThat(store.saveIfAbsent(hold("new-hold", List.of(10L, 20L)), TTL))
                .isEqualTo(HoldStore.SaveResult.HELD_BY_OTHER);

        assertThat(holder(10L)).isNull();
        assertThat(holder(20L)).isEqualTo("other-hold");
        assertThat(metaExists("new-hold")).isFalse();
    }

    @Test
    void 같은_회원이_먼저_잡은_좌석과_충돌하면_같은_회원이라고_알리고_아무것도_쓰지_않는다() {
        select(MEMBER_ID, 10L, 20L);
        assertThat(store.saveIfAbsent(hold("first-click", List.of(20L)), TTL)).isEqualTo(HoldStore.SaveResult.SAVED);

        assertThat(store.saveIfAbsent(hold("second-click", List.of(10L, 20L)), TTL))
                .isEqualTo(HoldStore.SaveResult.HELD_BY_SAME_MEMBER);

        assertThat(holder(10L)).isNull();
        assertThat(holder(20L)).isEqualTo("first-click");
        assertThat(metaExists("second-click")).isFalse();
    }

    @Test
    void 선점_확정은_본인_선택을_지우고_좌석마다_HELD를_보낸다() {
        select(MEMBER_ID, 10L, 20L);
        store.saveIfAbsent(hold("hold-ok", List.of(10L, 20L)), TTL);

        assertThat(store.confirmHeld(PERFORMANCE_ID, "hold-ok", MEMBER_ID, events(SeatStatusAction.HELD, 10L, 20L)))
                .isTrue();

        assertThat(selector(10L)).isNull();
        assertThat(selector(20L)).isNull();
        assertThat(redissonClient
                        .getScoredSortedSet(SeatSelectionRedisKey.selectSeatIndex(PERFORMANCE_ID), StringCodec.INSTANCE)
                        .readAll())
                .isEmpty();
        awaitRelayed(SeatStatusAction.HELD, SeatStatusAction.HELD);
    }

    /** 이미 풀린 hold에 HELD가 늦게 나가면 남이 고른 좌석이 선점된 것처럼 보인다. 확인과 발행이 한 스크립트라 그 사이에 끼지 못한다. */
    @Test
    void 이미_풀린_hold는_확정하지_않고_새_선택을_건드리지_않는다() {
        select(OTHER_MEMBER_ID, 10L);

        assertThat(store.confirmHeld(PERFORMANCE_ID, "stale-hold", MEMBER_ID, events(SeatStatusAction.HELD, 10L)))
                .isFalse();

        assertThat(selector(10L)).isEqualTo(OTHER_MEMBER_ID.toString());
        assertNothingRelayedAfterMarker();
    }

    @Test
    void 해제는_이_hold만_지우고_비어_있는_좌석만_RELEASED를_보내고_반복해도_같다() {
        select(MEMBER_ID, 10L, 20L);
        store.saveIfAbsent(hold("hold-mine", List.of(10L, 20L)), TTL);
        setHolder(20L, "other-hold");

        store.release(PERFORMANCE_ID, "hold-mine", events(SeatStatusAction.RELEASED, 10L, 20L));
        store.release(PERFORMANCE_ID, "hold-mine", events(SeatStatusAction.RELEASED, 10L, 20L));

        assertThat(holder(10L)).isNull();
        assertThat(holder(20L)).isEqualTo("other-hold");
        assertThat(metaExists("hold-mine")).isFalse();
        // 10번에는 아직 본인 선택 키가 남아 있어(확정 전 해제) 비어 있지 않다. 20번은 남의 선점이다.
        assertNothingRelayedAfterMarker();
    }

    /** 주문 만료: hold 키는 만료 시각에 TTL로 먼저 사라지고, 청소가 몇 초 뒤 해제를 부른다. 그때도 비어 있으면 알려야 다른 사용자 화면에서 풀린다. */
    @Test
    void TTL로_이미_풀린_좌석도_비어_있으면_RELEASED를_보낸다() {
        store.release(PERFORMANCE_ID, "expired-hold", events(SeatStatusAction.RELEASED, 10L));

        awaitRelayed(SeatStatusAction.RELEASED);
    }

    @Test
    void 다시_선택된_좌석은_해제해도_비었다고_알리지_않는다() {
        select(OTHER_MEMBER_ID, 10L);

        store.release(PERFORMANCE_ID, "expired-hold", events(SeatStatusAction.RELEASED, 10L));

        assertNothingRelayedAfterMarker();
    }

    @Test
    void 알림_없는_해제는_지우기만_한다() {
        select(MEMBER_ID, 10L);
        store.saveIfAbsent(hold("hold-mine", List.of(10L)), TTL);
        redissonClient
                .getBucket(SeatSelectionRedisKey.select(PERFORMANCE_ID, 10L))
                .delete();

        store.releaseWithoutNotice(PERFORMANCE_ID, "hold-mine", List.of(10L));

        assertThat(holder(10L)).isNull();
        assertThat(store.getHoldingSeatIds(PERFORMANCE_ID)).isEmpty();
        assertNothingRelayedAfterMarker();
    }

    @Test
    void 점유_인덱스는_만료된_좌석을_돌려주지_않는다() {
        redissonClient
                .getScoredSortedSet(HoldRedisKey.holdSeatIndex(PERFORMANCE_ID), StringCodec.INSTANCE)
                .add(1, "10");
        select(MEMBER_ID, 20L);
        store.saveIfAbsent(hold("hold-new", List.of(20L)), TTL);

        assertThat(store.getHoldingSeatIds(PERFORMANCE_ID)).containsExactly(20L);
    }

    private void select(final Long memberId, final Long... seatIds) {
        for (final Long seatId : seatIds) {
            redissonClient
                    .getBucket(SeatSelectionRedisKey.select(PERFORMANCE_ID, seatId), StringCodec.INSTANCE)
                    .set(memberId.toString(), TTL);
            redissonClient
                    .getScoredSortedSet(SeatSelectionRedisKey.selectSeatIndex(PERFORMANCE_ID), StringCodec.INSTANCE)
                    .add(Double.MAX_VALUE, seatId.toString());
        }
    }

    private void setHolder(final Long seatId, final String holdKey) {
        redissonClient
                .getBucket(HoldRedisKey.hold(PERFORMANCE_ID, seatId), StringCodec.INSTANCE)
                .set(holdKey, TTL);
    }

    private @Nullable String holder(final Long seatId) {
        return redissonClient
                .<String>getBucket(HoldRedisKey.hold(PERFORMANCE_ID, seatId), StringCodec.INSTANCE)
                .get();
    }

    private @Nullable String selector(final Long seatId) {
        return redissonClient
                .<String>getBucket(SeatSelectionRedisKey.select(PERFORMANCE_ID, seatId), StringCodec.INSTANCE)
                .get();
    }

    private boolean metaExists(final String holdKey) {
        return redissonClient
                        .getBucket(HoldRedisKey.holdMeta(holdKey), StringCodec.INSTANCE)
                        .get()
                != null;
    }

    private List<SeatStatusEvent> events(final SeatStatusAction action, final Long... seatIds) {
        return java.util.Arrays.stream(seatIds)
                .map(seatId -> new SeatStatusEvent(PERFORMANCE_ID, null, seatId, action, NOW))
                .toList();
    }

    private Hold hold(final String holdKey, final List<Long> seatIds) {
        return new Hold(
                holdKey, MEMBER_ID, PERFORMANCE_ID, seatIds, LocalDateTime.now().plusMinutes(5));
    }

    private void awaitRelayed(final SeatStatusAction... actions) {
        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() ->
                        assertThat(RELAYED).extracting(SeatStatusEvent::action).containsExactly(actions));
    }

    /** 같은 채널이라 표지 알림이 도착하면 앞서 발행됐을 알림도 이미 도착했다. 표지만 왔으면 앞의 호출은 아무것도 발행하지 않은 것이다. */
    private void assertNothingRelayedAfterMarker() {
        relayTopic.publish(
                JSON_MAPPER.writeValueAsString(new SeatStatusEvent(0L, null, 0L, SeatStatusAction.RELEASED, NOW)));
        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(RELAYED)
                        .extracting(SeatStatusEvent::performanceId)
                        .containsExactly(0L));
    }
}
