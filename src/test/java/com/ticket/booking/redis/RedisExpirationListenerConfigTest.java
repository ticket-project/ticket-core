package com.ticket.booking.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** 만료 처리 executor는 worker 2개와 무제한 큐로 처리 동시성을 제한한다. */
@SuppressWarnings("NonAsciiCharacters")
class RedisExpirationListenerConfigTest {
    private final RedisExpirationListenerConfig config = new RedisExpirationListenerConfig();

    @Test
    void 만료_executor는_고정_worker_2개와_무제한_큐를_갖는다() {
        final ThreadPoolTaskExecutor executor = config.redisExpirationTaskExecutor();
        executor.afterPropertiesSet();

        try {
            assertThat(executor.getCorePoolSize()).isEqualTo(2);
            assertThat(executor.getMaxPoolSize()).isEqualTo(2);
            assertThat(executor.getThreadPoolExecutor().getQueue().remainingCapacity())
                    .isEqualTo(Integer.MAX_VALUE);
            assertThat(executor.getThreadPoolExecutor().getRejectedExecutionHandler())
                    .isInstanceOf(ThreadPoolExecutor.AbortPolicy.class);
        } finally {
            executor.shutdown();
        }
    }

    /** queue가 없는 executor라 container가 처음 구독할 때 동시에 넘기는 작업을 받으려면 스레드가 둘 이상 필요하다. */
    @Test
    void subscription_executor_is_separate_and_supports_initial_registration_threads() {
        final ThreadPoolTaskExecutor executor = config.redisExpirationSubscriptionExecutor();
        executor.afterPropertiesSet();

        try {
            assertThat(executor.getThreadPoolExecutor().getQueue().remainingCapacity())
                    .isZero();
            assertThat(executor.getMaxPoolSize()).isGreaterThanOrEqualTo(2);
        } finally {
            executor.shutdown();
        }
    }

    @Test
    void 기존_큐_한도를_넘는_작업도_worker_2개에서만_실행된다() throws Exception {
        final ThreadPoolTaskExecutor executor = config.redisExpirationTaskExecutor();
        executor.afterPropertiesSet();
        final int workers = executor.getMaxPoolSize();
        final int taskCount = 300;
        final CountDownLatch release = new CountDownLatch(1);
        final AtomicInteger active = new AtomicInteger();
        final AtomicInteger maxActive = new AtomicInteger();
        final AtomicInteger completed = new AtomicInteger();
        final AtomicInteger outsideWorker = new AtomicInteger();
        final Runnable task = () -> {
            if (!Thread.currentThread().getName().startsWith("redis-expiration-")) {
                outsideWorker.incrementAndGet();
            }
            maxActive.accumulateAndGet(active.incrementAndGet(), Math::max);
            try {
                release.await();
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                active.decrementAndGet();
                completed.incrementAndGet();
            }
        };
        try {
            for (int i = 0; i < taskCount; i++) {
                executor.execute(task);
            }
            await().atMost(Duration.ofSeconds(5)).until(() -> active.get() == workers);
            assertThat(executor.getThreadPoolExecutor().getQueue()).hasSize(taskCount - workers);

            release.countDown();
            await().atMost(Duration.ofSeconds(10)).until(() -> completed.get() == taskCount);
            assertThat(maxActive).hasValue(2);
            assertThat(outsideWorker).hasValue(0);
        } finally {
            release.countDown();
            executor.shutdown();
        }
    }
}
