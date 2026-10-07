package com.ticket.booking.redis;

import java.util.Properties;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
public class RedisExpirationListenerConfig {
    private static final String EXPIRED_EVENT_PATTERN = "__keyevent@*__:expired";
    private static final String NOTIFY_KEYSPACE_EVENTS = "notify-keyspace-events";
    private static final String REQUIRED_NOTIFY_OPTIONS = "Ex";
    private static final int EXPIRATION_WORKER_COUNT = 2;

    @Bean
    public ThreadPoolTaskExecutor redisExpirationTaskExecutor() {
        final ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(EXPIRATION_WORKER_COUNT);
        executor.setMaxPoolSize(EXPIRATION_WORKER_COUNT);
        // 큐가 무제한이라 만료 폭주 때 처리 대기 작업이 메모리에 쌓인다.
        executor.setQueueCapacity(Integer.MAX_VALUE);
        executor.setThreadNamePrefix("redis-expiration-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        return executor;
    }

    @Bean
    public ThreadPoolTaskExecutor redisExpirationSubscriptionExecutor() {
        final ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(0);
        executor.setThreadNamePrefix("redis-expiration-subscription-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        return executor;
    }

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            final RedisConnectionFactory redisConnectionFactory,
            final RedisKeyExpirationListener redisKeyExpirationListener) {
        final RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(redisConnectionFactory);
        // @Configuration proxy가 같은 싱글턴 빈을 돌려준다.
        container.setTaskExecutor(redisExpirationTaskExecutor());
        container.setSubscriptionExecutor(redisExpirationSubscriptionExecutor());
        container.addMessageListener(redisKeyExpirationListener, new PatternTopic(EXPIRED_EVENT_PATTERN));
        return container;
    }

    @Bean
    public ApplicationRunner enableRedisKeyspaceNotifications(final RedisConnectionFactory redisConnectionFactory) {
        return args -> {
            try (RedisConnection connection = redisConnectionFactory.getConnection()) {
                final Properties config = connection.serverCommands().getConfig(NOTIFY_KEYSPACE_EVENTS);
                final String current = config.getProperty(NOTIFY_KEYSPACE_EVENTS, "");
                if (isExpiredEventsEnabled(current)) {
                    return;
                }

                connection.serverCommands().setConfig(NOTIFY_KEYSPACE_EVENTS, REQUIRED_NOTIFY_OPTIONS);
                log.info("레디스 키스페이스 알림을 활성화했습니다. 설정값={}", REQUIRED_NOTIFY_OPTIONS);
            } catch (final Exception e) {
                log.warn("레디스 키스페이스 알림 설정에 실패했습니다. 만료 이벤트 리스너가 동작하지 않을 수 있습니다.", e);
            }
        };
    }

    private boolean isExpiredEventsEnabled(final String current) {
        return current.contains("E") && current.contains("x");
    }
}
