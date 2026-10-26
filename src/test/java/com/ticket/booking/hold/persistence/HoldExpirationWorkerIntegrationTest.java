package com.ticket.booking.hold.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.Redisson;
import org.redisson.api.RScoredSortedSet;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.redisson.config.Config;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.ticket.booking.order.usecase.ExpireOrderUseCase;
import com.ticket.testsupport.TestContainerImages;

/** 주문 만료 장부에서 시각 지난 작업만, 한 인스턴스에만 꺼내 주는지와 Worker가 꺼낸 작업을 만료 처리에 넘기는지 실제 Redis로 확인한다. */
@Testcontainers
@SuppressWarnings("NonAsciiCharacters")
class HoldExpirationWorkerIntegrationTest {
    private static final int REDIS_PORT = 6379;
    private static final Instant NOW = Instant.parse("2026-10-26T01:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneId.of("Asia/Seoul"));

    @Container
    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(TestContainerImages.REDIS).withExposedPorts(REDIS_PORT);

    private static RedissonClient redissonClient;

    private final ExpireOrderUseCase expireOrderUseCase = mock(ExpireOrderUseCase.class);

    @BeforeAll
    static void setUpRedisClient() {
        final Config config = new Config();
        config.useSingleServer().setAddress("redis://" + REDIS.getHost() + ":" + REDIS.getMappedPort(REDIS_PORT));
        redissonClient = Redisson.create(config);
    }

    @AfterAll
    static void closeRedisClient() {
        redissonClient.shutdown();
    }

    @BeforeEach
    void flushRedis() {
        redissonClient.getKeys().flushall();
    }

    @Test
    void 시각이_지난_작업만_꺼내고_장부에서_지운다() {
        ledger().add(NOW.toEpochMilli() - 1, "past");
        ledger().add(NOW.toEpochMilli(), "now");
        ledger().add(NOW.toEpochMilli() + 1, "future");

        final List<String> due = worker().popDue(NOW);

        assertThat(due).containsExactlyInAnyOrder("past", "now");
        assertThat(ledger().readAll()).containsExactly("future");
    }

    /** 여러 인스턴스가 동시에 꺼내도 한 작업은 한 인스턴스만 가져간다. */
    @Test
    void 동시에_꺼내도_같은_작업을_두_번_주지_않는다() throws Exception {
        IntStream.range(0, 250).forEach(i -> ledger().add(NOW.toEpochMilli() - 1, "hold-" + i));
        final List<Callable<List<String>>> pollers = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            pollers.add(() -> {
                final List<String> taken = new ArrayList<>();
                List<String> batch;
                do {
                    batch = worker().popDue(NOW);
                    taken.addAll(batch);
                } while (!batch.isEmpty());
                return taken;
            });
        }

        final ExecutorService executor = Executors.newFixedThreadPool(8);
        final List<String> all = new ArrayList<>();
        try {
            for (final Future<List<String>> result : executor.invokeAll(pollers)) {
                all.addAll(result.get());
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(all).hasSize(250).doesNotHaveDuplicates();
        assertThat(ledger().isEmpty()).isTrue();
    }

    @Test
    void 꽉_채워_꺼냈으면_기다리지_않고_다음_묶음도_꺼낸다() {
        IntStream.range(0, HoldExpirationWorker.BATCH_SIZE + 1)
                .forEach(i -> ledger().add(NOW.toEpochMilli() - 1, "hold-" + i));
        final HoldExpirationWorker worker = worker();
        worker.start();
        try {
            worker.drainDue();

            assertThat(ledger().isEmpty()).isTrue();
            verify(expireOrderUseCase, timeout(5_000)).expireByHoldKey(eq("hold-100"), any(LocalDateTime.class));
        } finally {
            worker.stop();
        }
    }

    @Test
    void 시작하면_1초마다_확인해_만료_처리에_넘긴다() {
        ledger().add(NOW.toEpochMilli() - 1, "hold-due");
        ledger().add(NOW.toEpochMilli() + 60_000, "hold-later");
        final HoldExpirationWorker worker = worker();
        worker.start();
        try {
            verify(expireOrderUseCase, timeout(5_000))
                    .expireByHoldKey("hold-due", LocalDateTime.ofInstant(NOW, CLOCK.getZone()));
            verify(expireOrderUseCase, never()).expireByHoldKey(eq("hold-later"), any(LocalDateTime.class));
        } finally {
            worker.stop();
        }
        assertThat(worker.isRunning()).isFalse();
    }

    private HoldExpirationWorker worker() {
        return new HoldExpirationWorker(redissonClient, expireOrderUseCase, CLOCK);
    }

    private RScoredSortedSet<String> ledger() {
        return redissonClient.getScoredSortedSet(HoldRedisKey.holdExpiryLedger(), StringCodec.INSTANCE);
    }
}
