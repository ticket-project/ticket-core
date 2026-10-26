package com.ticket.booking.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.redisson.config.Config;
import org.redisson.spring.data.connection.RedissonConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.ticket.booking.hold.persistence.HoldRedisKey;
import com.ticket.booking.seat.port.SeatStatusEvent;
import com.ticket.booking.seat.port.SeatStatusEvent.SeatStatusAction;
import com.ticket.booking.selection.persistence.SeatSelectionRedisKey;
import com.ticket.testsupport.TestContainerImages;

import tools.jackson.databind.json.JsonMapper;

/** 발행 스크립트가 Redis 채널로 보내고, 구독자가 받아 그 회차 STOMP destination으로 그대로 보내는지 실제 Redis로 확인한다. */
@Testcontainers
@SuppressWarnings("NonAsciiCharacters")
class SeatStatusRelayIntegrationTest {
    private static final int REDIS_PORT = 6379;
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-03-15T01:00:00Z"), ZoneId.of("Asia/Seoul"));
    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    @Container
    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(TestContainerImages.REDIS).withExposedPorts(REDIS_PORT);

    private static RedissonClient redissonClient;
    private static RedisMessageListenerContainer container;
    private static final SimpMessagingTemplate MESSAGING_TEMPLATE = mock(SimpMessagingTemplate.class);

    private final RedisSeatStatusEventPublisher publisher =
            new RedisSeatStatusEventPublisher(redissonClient, JSON_MAPPER, CLOCK);

    @BeforeAll
    static void setUp() {
        final Config config = new Config();
        config.useSingleServer().setAddress("redis://" + REDIS.getHost() + ":" + REDIS.getMappedPort(REDIS_PORT));
        redissonClient = Redisson.create(config);

        final ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.initialize();
        container = new RedisMessageListenerContainer();
        container.setConnectionFactory(new RedissonConnectionFactory(redissonClient));
        container.setTaskExecutor(executor);
        container.addMessageListener(
                new SeatStatusRelaySubscriber(MESSAGING_TEMPLATE, JSON_MAPPER),
                new ChannelTopic(RedisSeatStatusEventPublisher.CHANNEL));
        container.afterPropertiesSet();
        container.start();
    }

    @AfterAll
    static void tearDown() throws Exception {
        container.destroy();
        redissonClient.shutdown();
    }

    /** 구독이 실제로 맺어질 때까지 시험 알림을 보내 본다. */
    @BeforeEach
    void waitUntilSubscribed() {
        redissonClient.getKeys().flushall();
        await().atMost(Duration.ofSeconds(10)).until(() -> {
            new RedisSeatStatusEventPublisher(redissonClient, JSON_MAPPER, CLOCK)
                    .publish(999L, null, 999L, SeatStatusAction.SELECTED);
            return !mockingDetails(MESSAGING_TEMPLATE).getInvocations().isEmpty();
        });
        await().pollDelay(Duration.ofMillis(200)).until(() -> true);
        clearInvocations(MESSAGING_TEMPLATE);
    }

    @Test
    void 발행한_알림이_구독자를_거쳐_같은_payload로_회차_destination에_간다() {
        publisher.publish(10L, 20L, 30L, SeatStatusAction.HELD);

        final ArgumentCaptor<SeatStatusEvent> captor = ArgumentCaptor.forClass(SeatStatusEvent.class);
        verify(MESSAGING_TEMPLATE, timeout(5_000)).convertAndSend(eq("/topic/performance/10/seats"), captor.capture());
        assertThat(captor.getValue())
                .isEqualTo(new SeatStatusEvent(
                        10L, 20L, 30L, SeatStatusAction.HELD, LocalDateTime.of(2026, 3, 15, 10, 0)));
    }

    @Test
    void 비어_있는_좌석이면_조건부_알림을_보낸다() {
        publisher.publishIfFree(10L, null, 30L, SeatStatusAction.RELEASED);

        final ArgumentCaptor<SeatStatusEvent> captor = ArgumentCaptor.forClass(SeatStatusEvent.class);
        verify(MESSAGING_TEMPLATE, timeout(5_000)).convertAndSend(eq("/topic/performance/10/seats"), captor.capture());
        assertThat(captor.getValue().action()).isEqualTo(SeatStatusAction.RELEASED);
        assertThat(captor.getValue().performanceSeatId()).isNull();
    }

    /** 누군가 다시 선택했거나 선점한 좌석을 비었다고 알리면, 남이 잡은 좌석이 화면에서 비어 보인다. */
    @Test
    void 선택이나_선점이_있는_좌석은_조건부_알림을_보내지_않는다() {
        redissonClient
                .getBucket(SeatSelectionRedisKey.select(10L, 30L), StringCodec.INSTANCE)
                .set("2");
        redissonClient
                .getBucket(HoldRedisKey.hold(10L, 31L), StringCodec.INSTANCE)
                .set("HOLD-1");

        publisher.publishIfFree(10L, null, 30L, SeatStatusAction.DESELECTED);
        publisher.publishIfFree(10L, null, 31L, SeatStatusAction.RELEASED);
        // 같은 채널·단일 스레드라 이 표지 알림이 도착하면 앞의 두 알림도 이미 처리됐다.
        publisher.publish(10L, null, 32L, SeatStatusAction.SELECTED);

        final ArgumentCaptor<SeatStatusEvent> captor = ArgumentCaptor.forClass(SeatStatusEvent.class);
        verify(MESSAGING_TEMPLATE, timeout(5_000)).convertAndSend(eq("/topic/performance/10/seats"), captor.capture());
        assertThat(captor.getAllValues()).extracting(SeatStatusEvent::seatId).containsExactly(32L);
    }
}
