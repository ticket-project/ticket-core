package com.ticket.core.infra.redis;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.ThreadPoolExecutor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@SuppressWarnings("NonAsciiCharacters")
class RedisExpirationListenerConfigTest {

    private final RedisExpirationListenerConfig config = new RedisExpirationListenerConfig();

    @Test
    void expiration_executor_has_fixed_workers_and_a_bounded_queue() {
        final ThreadPoolTaskExecutor executor = config.redisExpirationTaskExecutor();
        executor.afterPropertiesSet();

        try {
            assertThat(executor.getCorePoolSize()).isEqualTo(2);
            assertThat(executor.getMaxPoolSize()).isEqualTo(2);
            assertThat(executor.getThreadNamePrefix()).isEqualTo("redis-expiration-");
            assertThat(executor.getThreadPoolExecutor().getQueue().remainingCapacity()).isEqualTo(256);
            assertThat(executor.getThreadPoolExecutor().getRejectedExecutionHandler())
                    .isInstanceOf(ThreadPoolExecutor.CallerRunsPolicy.class);
        } finally {
            executor.shutdown();
        }
    }

    @Test
    void listener_container_uses_the_bounded_expiration_executor() {
        final RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);
        final RedisKeyExpirationListener listener = mock(RedisKeyExpirationListener.class);
        final ThreadPoolTaskExecutor executor = config.redisExpirationTaskExecutor();
        executor.afterPropertiesSet();

        try {
            final RedisMessageListenerContainer container = config.redisMessageListenerContainer(
                    connectionFactory,
                    listener,
                    executor
            );

            assertThat(ReflectionTestUtils.getField(container, "taskExecutor")).isSameAs(executor);
        } finally {
            executor.shutdown();
        }
    }
}
