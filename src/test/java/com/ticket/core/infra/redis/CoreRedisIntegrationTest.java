package com.ticket.booking.internal.infrastructure.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ticket.identity.internal.application.auth.token.AuthRefreshToken;
import com.ticket.booking.internal.infrastructure.performanceseat.store.SeatSelectionRedisKey;
import com.ticket.identity.internal.infrastructure.auth.token.RedisRefreshTokenStore;
import com.ticket.booking.internal.application.lock.LockKey;
import com.ticket.booking.internal.application.lock.LockManager;
import com.ticket.booking.internal.application.lock.LockOptions;
import com.ticket.booking.internal.infrastructure.lock.RedissonLockKeyFormatter;
import com.ticket.booking.internal.infrastructure.lock.RedissonLockManager;
import com.ticket.booking.internal.infrastructure.performanceseat.store.RedissonSeatSelectionStore;
import com.ticket.core.support.exception.CoreException;
import com.ticket.core.infra.support.UuidSupplier;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.redisson.config.Config;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class CoreRedisIntegrationTest {

    private static final int REDIS_PORT = 6379;
    private static final LockKey SAME_KEY = LockKey.seat(9_000L, 1L);
    private static final LockKey LEFT_KEY = LockKey.seat(9_000L, 2L);
    private static final LockKey RIGHT_KEY = LockKey.seat(9_000L, 3L);

    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(REDIS_PORT);

    private static RedissonClient redissonClient;

    @BeforeAll
    static void setUpRedisClient() {
        Config config = new Config();
        config.setCodec(StringCodec.INSTANCE);
        config.useSingleServer()
                .setAddress("redis://" + REDIS.getHost() + ":" + REDIS.getMappedPort(REDIS_PORT));
        redissonClient = Redisson.create(config);
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
    }

    @Test
    void concurrent_seat_selection_has_exactly_one_owner_and_expires_by_ttl() throws Exception {
        RedissonSeatSelectionStore store = new RedissonSeatSelectionStore(redissonClient);

        List<Boolean> acquired = runConcurrently(
                32,
                index -> store.selectIfAbsent(1L, 10L, "member-" + index, Duration.ofSeconds(5))
        );

        assertThat(acquired.stream().filter(Boolean::booleanValue)).hasSize(1);
        String owner = store.getHolder(1L, 10L);
        assertThat(owner).isNotBlank();
        assertThat(store.getSelectingSeatIds(1L)).containsExactly(10L);
        assertThat(store.releaseIfOwned(1L, 10L, "not-owner")).isFalse();
        assertThat(store.getHolder(1L, 10L)).isEqualTo(owner);
        assertThat(store.releaseIfOwned(1L, 10L, owner)).isTrue();
        assertThat(store.getHolder(1L, 10L)).isNull();
        assertThat(store.getSelectingSeatIds(1L)).isEmpty();

        assertThat(store.selectIfAbsent(1L, 10L, "expiring-owner", Duration.ofMillis(150))).isTrue();
        awaitCondition(() -> store.getHolder(1L, 10L) == null, "seat selection did not expire");
        assertThat(store.getSelectingSeatIds(1L)).isEmpty();
        assertThat(store.selectIfAbsent(1L, 10L, "next-owner", Duration.ofSeconds(1))).isTrue();
        assertThat(store.getSelectingSeatIds(1L)).containsExactly(10L);
        assertThat(store.selectIfAbsent(1L, 11L, "next-owner", Duration.ofSeconds(1))).isTrue();
        assertThat(store.selectIfAbsent(1L, 12L, "other-owner", Duration.ofSeconds(1))).isTrue();
        assertThat(store.releaseAllByMember(1L, "next-owner")).containsExactlyInAnyOrder(10L, 11L);
        assertThat(store.getSelectingSeatIds(1L)).containsExactly(12L);
    }

    @Test
    void seat_selection_index_expires_without_read_cleanup() throws Exception {
        RedissonSeatSelectionStore store = new RedissonSeatSelectionStore(redissonClient);
        String indexKey = SeatSelectionRedisKey.selectSeatIndex(1L);

        assertThat(store.selectIfAbsent(1L, 10L, "owner", Duration.ofMillis(150))).isTrue();
        assertThat(redissonClient.getKeys().countExists(indexKey)).isEqualTo(1L);

        awaitCondition(
                () -> redissonClient.getKeys().countExists(indexKey) == 0L,
                "seat selection index did not expire"
        );
    }

    @Test
    void refresh_token_can_be_consumed_only_once_under_concurrency() throws Exception {
        UUID tokenId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UuidSupplier uuidSupplier = () -> tokenId;
        RedisRefreshTokenStore store = new RedisRefreshTokenStore(redissonClient, uuidSupplier);
        String token = store.createRefreshToken(7L, 60L);
        AuthRefreshToken refreshToken = AuthRefreshToken.from(token);

        List<Optional<Long>> validated = runConcurrently(16, ignored -> store.validate(refreshToken));

        assertThat(validated.stream().flatMap(Optional::stream)).containsExactly(7L);
        assertThat(store.validateWithoutConsume(refreshToken)).isEmpty();
    }

    @Test
    void distributed_lock_serializes_same_key_but_not_different_keys() throws Exception {
        LockedService proxy = lockedService();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch firstEntered = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        try {
            Future<?> first = executor.submit(() -> proxy.execute(SAME_KEY, firstEntered, releaseFirst));
            assertThat(firstEntered.await(2, TimeUnit.SECONDS)).isTrue();

            assertThatThrownBy(() -> proxy.execute(
                    SAME_KEY,
                    new CountDownLatch(1),
                    new CountDownLatch(0)
            )).isInstanceOf(CoreException.class);

            releaseFirst.countDown();
            first.get(5, TimeUnit.SECONDS);

            CountDownLatch bothEntered = new CountDownLatch(2);
            CountDownLatch releaseBoth = new CountDownLatch(1);
            Future<?> left = executor.submit(() -> proxy.execute(LEFT_KEY, bothEntered, releaseBoth));
            Future<?> right = executor.submit(() -> proxy.execute(RIGHT_KEY, bothEntered, releaseBoth));
            assertThat(bothEntered.await(2, TimeUnit.SECONDS)).isTrue();
            releaseBoth.countDown();
            left.get(5, TimeUnit.SECONDS);
            right.get(5, TimeUnit.SECONDS);
        } finally {
            releaseFirst.countDown();
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private LockedService lockedService() {
        return new LockedService(new RedissonLockManager(redissonClient, new RedissonLockKeyFormatter()));
    }

    private void awaitCondition(
            final CheckedBooleanSupplier condition,
            final String failureMessage
    ) throws Exception {
        long deadlineNanos = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() >= deadlineNanos) {
                throw new AssertionError(failureMessage);
            }
            Thread.sleep(25L);
        }
    }

    private <T> List<T> runConcurrently(
            final int taskCount,
            final IntFunction<T> action
    ) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(taskCount);
        CountDownLatch ready = new CountDownLatch(taskCount);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<T>> futures = new ArrayList<>(taskCount);
        try {
            for (int index = 0; index < taskCount; index++) {
                int taskIndex = index;
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(5, TimeUnit.SECONDS)) {
                        throw new AssertionError("concurrent test did not start in time");
                    }
                    return action.apply(taskIndex);
                }));
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<T> results = new ArrayList<>(taskCount);
            for (Future<T> future : futures) {
                results.add(future.get(10, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            start.countDown();
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @FunctionalInterface
    private interface CheckedBooleanSupplier {
        boolean getAsBoolean() throws Exception;
    }

    /**
     * 락 안에서 오래 머무는 작업을 흉내 낸다. 실제 Redis로 상호 배제를 확인한다.
     */
    record LockedService(LockManager lockManager) {

        private static final LockOptions OPTIONS = LockOptions.waiting(Duration.ofMillis(100))
                .withLeaseTime(Duration.ofSeconds(5));

        void execute(
                final LockKey key,
                final CountDownLatch entered,
                final CountDownLatch release
        ) {
            lockManager.withLock(List.of(key), OPTIONS, () -> holdUntilReleased(entered, release));
        }

        private void holdUntilReleased(final CountDownLatch entered, final CountDownLatch release) {
            entered.countDown();
            try {
                if (!release.await(5, TimeUnit.SECONDS)) {
                    throw new AssertionError("locked operation was not released");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError("locked operation was interrupted", exception);
            }
        }
    }
}
