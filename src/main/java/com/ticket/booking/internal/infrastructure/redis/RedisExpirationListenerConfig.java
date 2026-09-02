package com.ticket.booking.internal.infrastructure.redis;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Properties;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;

@Slf4j
@Configuration
public class RedisExpirationListenerConfig {

    public static final String REDIS_EXPIRATION_TASK_EXECUTOR = "redisExpirationTaskExecutor";
    public static final String REDIS_EXPIRATION_SUBSCRIPTION_EXECUTOR = "redisExpirationSubscriptionExecutor";

    private static final String EXPIRED_EVENT_PATTERN = "__keyevent@*__:expired";
    private static final String NOTIFY_KEYSPACE_EVENTS = "notify-keyspace-events";
    private static final String REQUIRED_NOTIFY_OPTIONS = "Ex";
    private static final int EXPIRATION_WORKER_COUNT = 2;
    private static final int EXPIRATION_QUEUE_CAPACITY = 256;

    @Bean(name = REDIS_EXPIRATION_TASK_EXECUTOR)
    public ThreadPoolTaskExecutor redisExpirationTaskExecutor() {
        final Semaphore permits = new Semaphore(EXPIRATION_WORKER_COUNT, true);
        final ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(EXPIRATION_WORKER_COUNT);
        executor.setMaxPoolSize(EXPIRATION_WORKER_COUNT);
        executor.setQueueCapacity(EXPIRATION_QUEUE_CAPACITY);
        executor.setThreadNamePrefix("redis-expiration-");
        executor.setTaskDecorator(task -> () -> {
            permits.acquireUninterruptibly();
            try {
                task.run();
            } finally {
                permits.release();
            }
        });
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        return executor;
    }

    @Bean(name = REDIS_EXPIRATION_SUBSCRIPTION_EXECUTOR)
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
            final RedisKeyExpirationListener redisKeyExpirationListener,
            @Qualifier(REDIS_EXPIRATION_TASK_EXECUTOR) final Executor redisExpirationTaskExecutor,
            @Qualifier(REDIS_EXPIRATION_SUBSCRIPTION_EXECUTOR) final Executor redisExpirationSubscriptionExecutor
    ) {
        final RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(redisConnectionFactory);
        container.setTaskExecutor(redisExpirationTaskExecutor);
        container.setSubscriptionExecutor(redisExpirationSubscriptionExecutor);
        container.addMessageListener(redisKeyExpirationListener, new PatternTopic(EXPIRED_EVENT_PATTERN));
        return container;
    }

    @Bean
    public ApplicationRunner enableRedisKeyspaceNotifications(final RedisConnectionFactory redisConnectionFactory) {
        return args -> {
            RedisConnection connection = null;
            try {
                connection = redisConnectionFactory.getConnection();
                final Properties config = connection.serverCommands().getConfig(NOTIFY_KEYSPACE_EVENTS);
                final String current = config.getProperty(NOTIFY_KEYSPACE_EVENTS, "");
                if (supportsExpiredEvents(current)) {
                    return;
                }

                connection.serverCommands().setConfig(NOTIFY_KEYSPACE_EVENTS, REQUIRED_NOTIFY_OPTIONS);
                log.info("레디스 키스페이스 알림을 활성화했습니다. 설정값={}", REQUIRED_NOTIFY_OPTIONS);
            } catch (final Exception e) {
                log.warn("레디스 키스페이스 알림 설정에 실패했습니다. 만료 이벤트 리스너가 동작하지 않을 수 있습니다.", e);
            } finally {
                if (connection != null) {
                    connection.close();
                }
            }
        };
    }

    private boolean supportsExpiredEvents(final String current) {
        return current.contains("E") && current.contains("x");
    }
}
