package com.ticket.booking.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.redisson.config.Config;
import org.redisson.spring.data.connection.RedissonConnectionFactory;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.ticket.testsupport.TestContainerImages;

/**
 * 운영과 같은 조합(Redisson 연결 + {@link RedisExpirationListenerConfig}의 컨테이너·executor)으로 실제 Redis의 키 만료 알림이 handler까지 오는지
 * 확인한다. 기동 때 {@code CONFIG SET notify-keyspace-events}도 운영과 같은 {@code ApplicationRunner}로 켠다.
 */
@Testcontainers
@SuppressWarnings("NonAsciiCharacters")
class RedisKeyExpirationNotificationIntegrationTest {
    private static final int REDIS_PORT = 6379;

    @Container
    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(TestContainerImages.REDIS).withExposedPorts(REDIS_PORT);

    private final RedisExpirationListenerConfig config = new RedisExpirationListenerConfig();
    private final List<String> handled = new CopyOnWriteArrayList<>();

    private RedissonClient redissonClient;
    private RedisMessageListenerContainer container;
    private ThreadPoolTaskExecutor taskExecutor;
    private ThreadPoolTaskExecutor subscriptionExecutor;

    @BeforeEach
    void setUp() throws Exception {
        final Config redissonConfig = new Config();
        redissonConfig
                .useSingleServer()
                .setAddress("redis://" + REDIS.getHost() + ":" + REDIS.getMappedPort(REDIS_PORT));
        redissonClient = Redisson.create(redissonConfig);
        final RedissonConnectionFactory connectionFactory = new RedissonConnectionFactory(redissonClient);

        config.enableRedisKeyspaceNotifications(connectionFactory).run(null);

        taskExecutor = config.redisExpirationTaskExecutor();
        taskExecutor.initialize();
        subscriptionExecutor = config.redisExpirationSubscriptionExecutor();
        subscriptionExecutor.initialize();
        final RedisKeyExpirationListener listener = new RedisKeyExpirationListener(List.of(expiredKey -> {
            handled.add(expiredKey);
            return true;
        }));
        container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.setTaskExecutor(taskExecutor);
        // 구독 executor를 따로 주지 않으면 taskExecutor를 같이 쓴다. Redisson 구독은 끝날 때까지 스레드 하나를 쥐므로 taskExecutor가
        // 스레드 1개면 메시지를 처리할 스레드가 남지 않는다.
        container.setSubscriptionExecutor(subscriptionExecutor);
        container.addMessageListener(listener, new PatternTopic("__keyevent@*__:expired"));
        container.afterPropertiesSet();
        container.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        container.destroy();
        taskExecutor.shutdown();
        subscriptionExecutor.shutdown();
        redissonClient.shutdown();
    }

    @Test
    void TTL이_끝난_키의_만료_알림이_handler까지_온다() {
        await().atMost(Duration.ofSeconds(10)).until(container::isListening);

        redissonClient
                .getBucket("seat:select:{perf:7}:1234", StringCodec.INSTANCE)
                .set("1", Duration.ofMillis(300));

        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(handled).contains("seat:select:{perf:7}:1234"));
    }
}
