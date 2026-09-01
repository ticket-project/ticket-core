package com.ticket.core.infra.redis;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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
        final ThreadPoolTaskExecutor subscriptionExecutor = config.redisExpirationSubscriptionExecutor();
        executor.afterPropertiesSet();
        subscriptionExecutor.afterPropertiesSet();

        try {
            final RedisMessageListenerContainer container = config.redisMessageListenerContainer(
                    connectionFactory,
                    listener,
                    executor,
                    subscriptionExecutor
            );

            assertThat(ReflectionTestUtils.getField(container, "taskExecutor")).isSameAs(executor);
            assertThat(ReflectionTestUtils.getField(container, "subscriptionExecutor")).isSameAs(subscriptionExecutor);
        } finally {
            executor.shutdown();
            subscriptionExecutor.shutdown();
        }
    }

    @Test
    void subscription_executor_is_separate_and_supports_initial_registration_threads() {
        final ThreadPoolTaskExecutor executor = config.redisExpirationSubscriptionExecutor();
        executor.afterPropertiesSet();

        try {
            assertThat(executor.getCorePoolSize()).isEqualTo(1);
            assertThat(executor.getMaxPoolSize()).isEqualTo(2);
            assertThat(executor.getThreadNamePrefix()).isEqualTo("redis-expiration-subscription-");
            assertThat(executor.getThreadPoolExecutor().getQueue().remainingCapacity()).isZero();
        } finally {
            executor.shutdown();
        }
    }

    @Test
    void expiration_task_decorator_limits_parallel_handler_entry_to_two() throws Exception {
        final ThreadPoolTaskExecutor executor = config.redisExpirationTaskExecutor();
        final TaskDecorator taskDecorator = (TaskDecorator) ReflectionTestUtils.getField(executor, "taskDecorator");
        final ExecutorService callers = Executors.newFixedThreadPool(3);
        final CountDownLatch twoTasksEntered = new CountDownLatch(2);
        final CountDownLatch releaseTasks = new CountDownLatch(1);
        final AtomicInteger activeTasks = new AtomicInteger();
        final AtomicInteger maxActiveTasks = new AtomicInteger();
        final Runnable task = taskDecorator.decorate(() -> {
            final int active = activeTasks.incrementAndGet();
            maxActiveTasks.accumulateAndGet(active, Math::max);
            twoTasksEntered.countDown();
            try {
                releaseTasks.await();
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                activeTasks.decrementAndGet();
            }
        });

        try {
            final List<Future<?>> futures = List.of(
                    callers.submit(task),
                    callers.submit(task),
                    callers.submit(task)
            );

            assertThat(twoTasksEntered.await(Duration.ofSeconds(5).toMillis(), TimeUnit.MILLISECONDS)).isTrue();
            assertThat(activeTasks).hasValue(2);
            assertThat(maxActiveTasks).hasValue(2);

            releaseTasks.countDown();
            for (final Future<?> future : futures) {
                future.get(5, TimeUnit.SECONDS);
            }
            assertThat(maxActiveTasks).hasValue(2);
        } finally {
            releaseTasks.countDown();
            callers.shutdownNow();
        }
    }
}
