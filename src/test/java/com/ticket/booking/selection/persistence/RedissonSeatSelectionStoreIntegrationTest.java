package com.ticket.booking.selection.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.mock;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

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

import com.ticket.booking.hold.persistence.HoldRedisKey;
import com.ticket.booking.seat.port.SeatStatusEvent;
import com.ticket.booking.seat.port.SeatStatusEvent.SeatStatusAction;
import com.ticket.booking.selection.domain.SeatSelectionStore.SelectResult;
import com.ticket.booking.websocket.SeatStatusRelay;
import com.ticket.testsupport.TestContainerImages;

import tools.jackson.databind.json.JsonMapper;

/**
 * 좌석 선택 스크립트가 <b>실제 Redis</b>에서 선점 확인·상태 변경·relay 발행을 한 번에 하는지 확인한다. 좌석 락이 없으므로, 선택과 선점이 한 좌석에 함께 생기지 않는 것과 이미 남이 잡은
 * 좌석을 비었다고 알리지 않는 것을 여기서 고정한다.
 */
@Testcontainers
@SuppressWarnings("NonAsciiCharacters")
class RedissonSeatSelectionStoreIntegrationTest {
    private static final int REDIS_PORT = 6379;
    private static final long PERFORMANCE_ID = 9_200L;
    private static final long SEAT_ID = 10L;
    private static final Duration TTL = Duration.ofMinutes(5);
    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    @Container
    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(TestContainerImages.REDIS).withExposedPorts(REDIS_PORT);

    private static RedissonClient redissonClient;
    private static RTopic relayTopic;
    private static final List<SeatStatusEvent> RELAYED = new CopyOnWriteArrayList<>();

    private final RedissonSeatSelectionStore store = new RedissonSeatSelectionStore(
            redissonClient, new SeatStatusRelay(mock(SimpMessagingTemplate.class), JSON_MAPPER));

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
    void 선택하면_SELECTED가_relay로_나간다() {
        assertThat(store.selectIfAbsent(PERFORMANCE_ID, SEAT_ID, "1", TTL, null, event(SeatStatusAction.SELECTED)))
                .isEqualTo(SelectResult.SELECTED);

        awaitRelayed(SeatStatusAction.SELECTED);
    }

    /** 주문 선점이 먼저 잡은 좌석은 선택되지 않는다. 확인과 기록이 한 스크립트라 그 사이에 선점이 끼지 못한다. */
    @Test
    void 선점된_좌석은_선택하지_않고_알리지도_않는다() {
        holdSeat("HOLD-1");

        assertThat(store.selectIfAbsent(PERFORMANCE_ID, SEAT_ID, "1", TTL, null, event(SeatStatusAction.SELECTED)))
                .isEqualTo(SelectResult.HELD);

        assertThat(store.getHolder(PERFORMANCE_ID, SEAT_ID)).isNull();
        assertNothingRelayedAfterMarker();
    }

    @Test
    void 본인_선택을_해제하면_DESELECTED가_나간다() {
        store.selectIfAbsent(PERFORMANCE_ID, SEAT_ID, "1", TTL, null, event(SeatStatusAction.SELECTED));

        assertThat(store.releaseIfOwned(PERFORMANCE_ID, SEAT_ID, "1", event(SeatStatusAction.DESELECTED)))
                .isTrue();

        awaitRelayed(SeatStatusAction.SELECTED, SeatStatusAction.DESELECTED);
    }

    /** 주문을 만든 뒤 남아 있던 선택을 해제해도 HELD 상태를 "비었음"으로 덮어쓰지 않는다. */
    @Test
    void 선점으로_넘어간_좌석의_선택을_해제하면_알리지_않는다() {
        store.selectIfAbsent(PERFORMANCE_ID, SEAT_ID, "1", TTL, null, event(SeatStatusAction.SELECTED));
        holdSeat("HOLD-1");
        awaitRelayed(SeatStatusAction.SELECTED);
        RELAYED.clear();

        assertThat(store.releaseIfOwned(PERFORMANCE_ID, SEAT_ID, "1", event(SeatStatusAction.DESELECTED)))
                .isTrue();

        assertNothingRelayedAfterMarker();
    }

    /** 결함 재현: A의 선택이 만료되고 B가 같은 좌석을 다시 고른 뒤 A의 만료 알림이 뒤늦게 오는 경우다. 알리면 B가 잡은 좌석이 비어 보인다. */
    @Test
    void 만료_알림은_다시_선택되거나_선점된_좌석이면_나가지_않는다() {
        store.selectIfAbsent(PERFORMANCE_ID, SEAT_ID, "2", TTL, null, event(SeatStatusAction.SELECTED));
        redissonClient.getBucket(HoldRedisKey.hold(PERFORMANCE_ID, SEAT_ID + 1)).set("HOLD-1");
        awaitRelayed(SeatStatusAction.SELECTED);
        RELAYED.clear();

        store.publishIfFree(PERFORMANCE_ID, SEAT_ID, event(SeatStatusAction.DESELECTED));
        store.publishIfFree(PERFORMANCE_ID, SEAT_ID + 1, event(SeatStatusAction.DESELECTED));

        assertNothingRelayedAfterMarker();
    }

    @Test
    void 만료_알림은_비어_있는_좌석이면_나간다() {
        store.publishIfFree(PERFORMANCE_ID, SEAT_ID, event(SeatStatusAction.DESELECTED));

        awaitRelayed(SeatStatusAction.DESELECTED);
    }

    /** 만료 알림은 Redis 만료 알림이 아니라 선택과 함께 적은 장부에서 꺼낸다. 시각이 지난 항목만, 한 번만 꺼낸다. */
    @Test
    void 선택은_만료_장부에_적히고_시각이_지난_항목만_한_번_꺼내진다() {
        store.selectIfAbsent(
                PERFORMANCE_ID, SEAT_ID, "1", Duration.ofMillis(100), null, event(SeatStatusAction.SELECTED));
        store.selectIfAbsent(PERFORMANCE_ID, SEAT_ID + 1, "2", TTL, null, event(SeatStatusAction.SELECTED));

        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(store.popExpired(100))
                        .singleElement()
                        .satisfies(expired -> {
                            assertThat(expired.performanceId()).isEqualTo(PERFORMANCE_ID);
                            assertThat(expired.seatId()).isEqualTo(SEAT_ID);
                            assertThat(expired.delayMillis()).isNotNegative();
                        }));
        assertThat(store.popExpired(100)).isEmpty();
    }

    /** 장부 값이 "회차:좌석"이라 같은 좌석을 다시 고르면 항목이 늘지 않고 점수만 새 만료 시각으로 바뀐다. 예전 시각에는 꺼내지지 않는다. */
    @Test
    void 같은_좌석을_다시_고르면_장부_항목은_하나이고_새_만료_시각을_따른다() {
        store.selectIfAbsent(
                PERFORMANCE_ID, SEAT_ID, "1", Duration.ofMillis(100), null, event(SeatStatusAction.SELECTED));
        await().atMost(Duration.ofSeconds(5)).until(() -> store.getHolder(PERFORMANCE_ID, SEAT_ID) == null);
        store.selectIfAbsent(PERFORMANCE_ID, SEAT_ID, "2", TTL, null, event(SeatStatusAction.SELECTED));

        assertThat(store.popExpired(100)).isEmpty();
        assertThat(redissonClient
                        .getScoredSortedSet(SeatSelectionRedisKey.SELECT_EXPIRY_LEDGER_KEY, StringCodec.INSTANCE)
                        .size())
                .isEqualTo(1);
    }

    private void holdSeat(final String holdKey) {
        redissonClient.getBucket(HoldRedisKey.hold(PERFORMANCE_ID, SEAT_ID)).set(holdKey);
    }

    private SeatStatusEvent event(final SeatStatusAction action) {
        return new SeatStatusEvent(PERFORMANCE_ID, null, SEAT_ID, action, LocalDateTime.of(2026, 3, 15, 10, 0));
    }

    private void awaitRelayed(final SeatStatusAction... actions) {
        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() ->
                        assertThat(RELAYED).extracting(SeatStatusEvent::action).containsExactly(actions));
    }

    /** 같은 채널이라 표지 알림이 도착하면 앞서 발행됐을 알림도 이미 도착했다. 표지만 왔으면 앞의 호출은 아무것도 발행하지 않은 것이다. */
    private void assertNothingRelayedAfterMarker() {
        relayTopic.publish(JSON_MAPPER.writeValueAsString(
                new SeatStatusEvent(0L, null, 0L, SeatStatusAction.RELEASED, LocalDateTime.of(2026, 3, 15, 10, 0))));
        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(RELAYED)
                        .extracting(SeatStatusEvent::performanceId)
                        .containsExactly(0L));
    }
}
