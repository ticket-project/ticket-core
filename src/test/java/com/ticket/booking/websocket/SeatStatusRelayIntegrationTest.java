package com.ticket.booking.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
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

import com.ticket.booking.seat.port.SeatStatusEvent;
import com.ticket.booking.seat.port.SeatStatusEvent.SeatStatusAction;
import com.ticket.booking.websocket.SeatStatusRelay.SeatStatusBatch;
import com.ticket.booking.websocket.SeatStatusRelay.SeatStatusChange;
import com.ticket.testsupport.TestContainerImages;

import tools.jackson.databind.json.JsonMapper;

/**
 * 운영과 같은 조합(Redisson 연결 + {@link SeatStatusRelayConfig}의 컨테이너·executor)으로 채널에 올린 알림이 회차 destination에 묶음으로 가는지 확인한다. 묶음
 * 전송 스레드 대신 테스트가 {@link SeatStatusRelay#flush()}를 직접 부른다.
 */
@Testcontainers
@SuppressWarnings("NonAsciiCharacters")
class SeatStatusRelayIntegrationTest {
    private static final int REDIS_PORT = 6379;

    @Container
    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(TestContainerImages.REDIS).withExposedPorts(REDIS_PORT);

    private final SeatStatusRelayConfig config = new SeatStatusRelayConfig();
    private final SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
    private final SeatStatusRelay relay =
            new SeatStatusRelay(messagingTemplate, JsonMapper.builder().build());

    private RedissonClient redissonClient;
    private RedisMessageListenerContainer container;
    private ThreadPoolTaskExecutor taskExecutor;
    private ThreadPoolTaskExecutor subscriptionExecutor;

    @BeforeEach
    void setUp() {
        final Config redissonConfig = new Config();
        redissonConfig
                .useSingleServer()
                .setAddress("redis://" + REDIS.getHost() + ":" + REDIS.getMappedPort(REDIS_PORT));
        redissonClient = Redisson.create(redissonConfig);

        taskExecutor = config.seatStatusRelayExecutor();
        taskExecutor.initialize();
        subscriptionExecutor = config.seatStatusRelaySubscriptionExecutor();
        subscriptionExecutor.initialize();
        container = new RedisMessageListenerContainer();
        container.setConnectionFactory(new RedissonConnectionFactory(redissonClient));
        container.setTaskExecutor(taskExecutor);
        container.setSubscriptionExecutor(subscriptionExecutor);
        container.addMessageListener(relay, new ChannelTopic(SeatStatusRelay.CHANNEL));
        container.afterPropertiesSet();
        container.start();
        await().atMost(Duration.ofSeconds(10)).until(container::isListening);
    }

    @AfterEach
    void tearDown() throws Exception {
        container.destroy();
        taskExecutor.shutdown();
        subscriptionExecutor.shutdown();
        redissonClient.shutdown();
    }

    @Test
    void 채널에_올린_알림이_묶음으로_회차_destination에_간다() {
        final SeatStatusEvent event =
                new SeatStatusEvent(10L, 20L, 30L, SeatStatusAction.HELD, LocalDateTime.of(2026, 3, 15, 10, 0));

        publish(event);

        final ArgumentCaptor<SeatStatusBatch> captor = ArgumentCaptor.forClass(SeatStatusBatch.class);
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            relay.flush();
            verify(messagingTemplate).convertAndSend(eq("/topic/performance/10/seats"), captor.capture());
        });
        assertThat(captor.getValue())
                .isEqualTo(new SeatStatusBatch(10L, List.of(new SeatStatusChange(20L, 30L, SeatStatusAction.HELD))));
    }

    /** 채널에 올린 순서대로 받는다. 한 묶음 안에서는 같은 좌석의 마지막 상태가 남는다. */
    @Test
    void 채널에_올린_순서대로_받아_같은_좌석은_마지막_상태를_보낸다() {
        final List<SeatStatusAction> actions =
                List.of(SeatStatusAction.SELECTED, SeatStatusAction.DESELECTED, SeatStatusAction.SELECTED);
        actions.forEach(
                action -> publish(new SeatStatusEvent(10L, null, 30L, action, LocalDateTime.of(2026, 3, 15, 10, 0))));
        publish(new SeatStatusEvent(10L, null, 31L, SeatStatusAction.HELD, LocalDateTime.of(2026, 3, 15, 10, 0)));

        // 네 메시지가 모두 도착한 뒤(마지막 좌석 31이 보인 뒤) 한 번에 보낸다.
        final ArgumentCaptor<SeatStatusBatch> captor = ArgumentCaptor.forClass(SeatStatusBatch.class);
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            relay.flush();
            verify(messagingTemplate, atLeastOnce())
                    .convertAndSend(eq("/topic/performance/10/seats"), captor.capture());
            assertThat(captor.getAllValues())
                    .flatExtracting(SeatStatusBatch::changes)
                    .extracting(SeatStatusChange::seatId)
                    .contains(31L);
        });
        final List<SeatStatusChange> lastStates = captor.getAllValues().stream()
                .flatMap(batch -> batch.changes().stream())
                .filter(change -> change.seatId().equals(30L))
                .toList();
        assertThat(lastStates).last().extracting(SeatStatusChange::action).isEqualTo(SeatStatusAction.SELECTED);
    }

    private void publish(final SeatStatusEvent event) {
        redissonClient.getTopic(SeatStatusRelay.CHANNEL, StringCodec.INSTANCE).publish(relay.encode(event));
    }
}
