package com.ticket.booking.concurrency.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;
import java.util.function.Supplier;

import org.awaitility.core.ConditionFactory;
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

import com.ticket.booking.concurrency.LockKey;
import com.ticket.booking.concurrency.LockManager;
import com.ticket.booking.concurrency.LockOptions;
import com.ticket.booking.exception.HoldBusyException;
import com.ticket.booking.selection.domain.SeatSelectionStore.SelectResult;
import com.ticket.booking.selection.persistence.RedissonSeatSelectionStore;
import com.ticket.booking.selection.persistence.SeatSelectionRedisKey;
import com.ticket.security.token.AuthRefreshToken;
import com.ticket.security.token.RedisRefreshTokenStore;

@Testcontainers
class CoreRedisIntegrationTest {
    private static final int REDIS_PORT = 6379;
    private static final LockKey SAME_KEY = LockKey.seat(9_000L, 1L);
    private static final LockKey LEFT_KEY = LockKey.seat(9_000L, 2L);
    private static final LockKey RIGHT_KEY = LockKey.seat(9_000L, 3L);

    @Container
    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine")).withExposedPorts(REDIS_PORT);

    private static RedissonClient redissonClient;

    @BeforeAll
    static void setUpRedisClient() {
        Config config = new Config();
        config.setCodec(StringCodec.INSTANCE);
        config.useSingleServer().setAddress("redis://" + REDIS.getHost() + ":" + REDIS.getMappedPort(REDIS_PORT));
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

        List<SelectResult> acquired = runConcurrently(
                32, index -> store.selectIfAbsent(1L, 10L, "member-" + index, Duration.ofSeconds(5), null));

        assertThat(acquired).filteredOn(SelectResult.SELECTED::equals).hasSize(1);
        String owner = store.getHolder(1L, 10L);
        assertThat(owner).isNotBlank();
        assertThat(store.getSelectingSeatIds(1L)).containsExactly(10L);
        assertThat(store.releaseIfOwned(1L, 10L, "not-owner")).isFalse();
        assertThat(store.getHolder(1L, 10L)).isEqualTo(owner);
        assertThat(store.releaseIfOwned(1L, 10L, owner)).isTrue();
        assertThat(store.getHolder(1L, 10L)).isNull();
        assertThat(store.getSelectingSeatIds(1L)).isEmpty();

        assertThat(store.selectIfAbsent(1L, 10L, "expiring-owner", Duration.ofMillis(150), null))
                .isEqualTo(SelectResult.SELECTED);
        awaitExpiry("seat selection did not expire").until(() -> store.getHolder(1L, 10L) == null);
        assertThat(store.getSelectingSeatIds(1L)).isEmpty();
        assertThat(store.selectIfAbsent(1L, 10L, "next-owner", Duration.ofSeconds(1), null))
                .isEqualTo(SelectResult.SELECTED);
        assertThat(store.getSelectingSeatIds(1L)).containsExactly(10L);
        assertThat(store.selectIfAbsent(1L, 11L, "next-owner", Duration.ofSeconds(1), null))
                .isEqualTo(SelectResult.SELECTED);
        assertThat(store.selectIfAbsent(1L, 12L, "other-owner", Duration.ofSeconds(1), null))
                .isEqualTo(SelectResult.SELECTED);
        assertThat(store.releaseAllByMember(1L, "next-owner")).containsExactlyInAnyOrder(10L, 11L);
        assertThat(store.getSelectingSeatIds(1L)).containsExactly(12L);
    }

    @Test
    void member_selection_limit_holds_under_concurrency_and_frees_on_release() throws Exception {
        RedissonSeatSelectionStore store = new RedissonSeatSelectionStore(redissonClient);

        List<SelectResult> results = runConcurrently(
                16, index -> store.selectIfAbsent(1L, 100L + index, "member", Duration.ofSeconds(5), 2));

        assertThat(results).filteredOn(SelectResult.SELECTED::equals).hasSize(2);
        assertThat(results).filteredOn(SelectResult.LIMIT_EXCEEDED::equals).hasSize(14);
        Set<Long> selected = store.getSelectedSeatIdsByMember(1L, "member");
        assertThat(selected).hasSize(2);
        assertThat(store.getSelectedSeatIdsByMember(1L, "other")).isEmpty();

        Long released = selected.iterator().next();
        assertThat(store.releaseIfOwned(1L, released, "member")).isTrue();
        assertThat(store.getSelectedSeatIdsByMember(1L, "member")).hasSize(1);
        assertThat(store.selectIfAbsent(1L, 200L, "member", Duration.ofSeconds(5), 2))
                .isEqualTo(SelectResult.SELECTED);
    }

    @Test
    void expired_selection_no_longer_counts_toward_member_limit() throws Exception {
        RedissonSeatSelectionStore store = new RedissonSeatSelectionStore(redissonClient);

        assertThat(store.selectIfAbsent(1L, 10L, "member", Duration.ofMillis(150), 1))
                .isEqualTo(SelectResult.SELECTED);
        assertThat(store.selectIfAbsent(1L, 11L, "member", Duration.ofSeconds(5), 1))
                .isEqualTo(SelectResult.LIMIT_EXCEEDED);

        awaitExpiry("seat selection did not expire").until(() -> store.getHolder(1L, 10L) == null);
        assertThat(store.getSelectedSeatIdsByMember(1L, "member")).isEmpty();
        assertThat(store.selectIfAbsent(1L, 11L, "member", Duration.ofSeconds(5), 1))
                .isEqualTo(SelectResult.SELECTED);
    }

    @Test
    void member_index_remembers_expired_selection_but_forgets_explicit_release() throws Exception {
        RedissonSeatSelectionStore store = new RedissonSeatSelectionStore(redissonClient);

        assertThat(store.selectIfAbsent(1L, 10L, "member", Duration.ofMillis(150), null))
                .isEqualTo(SelectResult.SELECTED);
        assertThat(store.selectIfAbsent(1L, 11L, "member", Duration.ofSeconds(5), null))
                .isEqualTo(SelectResult.SELECTED);
        assertThat(store.getRecentlyExpiredSeatIdsByMember(1L, "member")).isEmpty();

        awaitExpiry("seat selection did not expire").until(() -> store.getHolder(1L, 10L) == null);
        assertThat(store.getRecentlyExpiredSeatIdsByMember(1L, "member")).containsExactly(10L);
        assertThat(store.getSelectedSeatIdsByMember(1L, "member")).containsExactly(11L);
        assertThat(store.getRecentlyExpiredSeatIdsByMember(1L, "other")).isEmpty();

        assertThat(store.releaseIfOwned(1L, 11L, "member")).isTrue();
        assertThat(store.getSelectedSeatIdsByMember(1L, "member")).isEmpty();
        assertThat(store.getRecentlyExpiredSeatIdsByMember(1L, "member")).containsExactly(10L);

        // 만료 뒤 다시 고르면 만료 기록이 아니라 선택 중으로 돌아온다.
        assertThat(store.selectIfAbsent(1L, 10L, "member", Duration.ofSeconds(5), null))
                .isEqualTo(SelectResult.SELECTED);
        assertThat(store.getRecentlyExpiredSeatIdsByMember(1L, "member")).isEmpty();
    }

    @Test
    void seat_selection_index_expires_without_read_cleanup() throws Exception {
        RedissonSeatSelectionStore store = new RedissonSeatSelectionStore(redissonClient);
        String indexKey = SeatSelectionRedisKey.selectSeatIndex(1L);

        assertThat(store.selectIfAbsent(1L, 10L, "owner", Duration.ofMillis(150), null))
                .isEqualTo(SelectResult.SELECTED);
        assertThat(redissonClient.getKeys().countExists(indexKey)).isEqualTo(1L);

        awaitExpiry("seat selection index did not expire")
                .until(() -> redissonClient.getKeys().countExists(indexKey) == 0L);
    }

    @Test
    void refresh_token_can_be_consumed_only_once_under_concurrency() throws Exception {
        UUID tokenId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        Supplier<UUID> uuidSupplier = () -> tokenId;
        RedisRefreshTokenStore store = new RedisRefreshTokenStore(redissonClient, uuidSupplier);
        String token = store.createRefreshToken(7L, 60L);
        AuthRefreshToken refreshToken = AuthRefreshToken.from(token);

        List<Optional<Long>> consumed = runConcurrently(16, ignored -> store.consume(refreshToken));

        assertThat(consumed.stream().flatMap(Optional::stream)).containsExactly(7L);
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

            assertThatThrownBy(() -> proxy.execute(SAME_KEY, new CountDownLatch(1), new CountDownLatch(0)))
                    .isInstanceOf(HoldBusyException.class);

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

    /** 만료는 Redis가 비동기로 처리하므로 조건이 참이 될 때까지 기다린다. */
    private static ConditionFactory awaitExpiry(final String failureMessage) {
        return await(failureMessage).atMost(Duration.ofSeconds(5)).pollInterval(Duration.ofMillis(25));
    }

    private <T> List<T> runConcurrently(final int taskCount, final IntFunction<T> action) throws Exception {
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

    /** 락 안에서 오래 머무는 작업을 흉내 낸다. 실제 Redis로 상호 배제를 확인한다. */
    record LockedService(LockManager lockManager) {
        private static final LockOptions OPTIONS = LockOptions.waiting(Duration.ofMillis(100));

        void execute(final LockKey key, final CountDownLatch entered, final CountDownLatch release) {
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
