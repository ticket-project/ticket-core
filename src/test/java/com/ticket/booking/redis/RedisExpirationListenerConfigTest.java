package com.ticket.booking.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 만료 처리 executor의 불변식만 고정한다 — 고정 크기 worker, 유한한 queue, 넘치면 호출 스레드가 처리(CallerRuns)하되 그때도 동시 처리 수는 worker 수를 넘지 않는 것.
 * worker 수·queue 크기 같은 조정 값은 {@link RedisExpirationListenerConfig}가 원본이다.
 */
@SuppressWarnings("NonAsciiCharacters")
class RedisExpirationListenerConfigTest {
    private final RedisExpirationListenerConfig config = new RedisExpirationListenerConfig();

    @Test
    void expiration_executor_has_fixed_workers_and_a_bounded_queue() {
        final ThreadPoolTaskExecutor executor = config.redisExpirationTaskExecutor();
        executor.afterPropertiesSet();

        try {
            assertThat(executor.getMaxPoolSize()).isEqualTo(executor.getCorePoolSize());
            assertThat(executor.getThreadPoolExecutor().getQueue().remainingCapacity())
                    .isPositive()
                    .isLessThan(Integer.MAX_VALUE);
            assertThat(executor.getThreadPoolExecutor().getRejectedExecutionHandler())
                    .isInstanceOf(ThreadPoolExecutor.CallerRunsPolicy.class);
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

    /** worker와 queue가 모두 찬 뒤 CallerRuns로 호출 스레드에서 도는 작업도 handler에 동시에 들어가는 수는 worker 수를 넘지 않는다. */
    @Test
    void caller_runs_overflow_does_not_exceed_worker_count() throws Exception {
        final ThreadPoolTaskExecutor executor = config.redisExpirationTaskExecutor();
        executor.afterPropertiesSet();
        final int workers = executor.getMaxPoolSize();
        final CountDownLatch release = new CountDownLatch(1);
        final AtomicInteger active = new AtomicInteger();
        final AtomicInteger maxActive = new AtomicInteger();
        final AtomicInteger completed = new AtomicInteger();
        final Runnable task = () -> {
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
        final Thread overflowCaller = new Thread(() -> executor.execute(task), "overflow-caller");

        try {
            final int queueCapacity =
                    executor.getThreadPoolExecutor().getQueue().remainingCapacity();
            for (int i = 0; i < workers + queueCapacity; i++) {
                executor.execute(task);
            }
            await().atMost(Duration.ofSeconds(5)).until(() -> active.get() == workers);

            overflowCaller.start();
            await().atMost(Duration.ofSeconds(5)).until(() -> overflowCaller.getState() == Thread.State.WAITING);
            assertThat(active).hasValue(workers);

            release.countDown();
            overflowCaller.join(Duration.ofSeconds(5));
            await().atMost(Duration.ofSeconds(10)).until(() -> completed.get() == workers + queueCapacity + 1);
            assertThat(maxActive).hasValue(workers);
        } finally {
            release.countDown();
            executor.shutdown();
        }
    }
}
