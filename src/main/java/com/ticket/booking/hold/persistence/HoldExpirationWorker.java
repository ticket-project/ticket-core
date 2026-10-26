package com.ticket.booking.hold.persistence;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.jspecify.annotations.Nullable;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.context.SmartLifecycle;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import com.ticket.booking.order.usecase.ExpireOrderUseCase;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 주문 만료 장부({@link HoldRedisKey#holdExpiryLedger()})를 1초마다 확인해, 만료 시각이 지난 주문을 만료시킨다(스케일 아웃 결정 목록 D2-2).
 *
 * <p><b>모든 인스턴스가 각자 돈다.</b> 장부에서 꺼내기는 "시각 지난 작업 읽기 + 지우기"를 Lua 하나로 해서, 여러 인스턴스가 동시에 확인해도 한 작업은 한 인스턴스만 가져간다(원자적 claim).
 * {@code @Scheduled}는 {@code worker.enabled=true}인 인스턴스에서만 켜지므로 쓰지 않고 자기 스레드로 돈다.
 *
 * <ul>
 *   <li>한 번에 최대 {@value #BATCH_SIZE}개를 꺼낸다. 꽉 채워 꺼냈으면 기다리지 않고 바로 다시 꺼낸다(만료가 몰릴 때).
 *   <li>시각은 앱 서버 시계로 비교한다. 장부 점수가 주문 {@code expiresAt}과 같은 시계이고, {@code Order.expire}가 만료 시각 전에는 거부하기 때문이다.
 *   <li>꺼낸 뒤 처리하기 전에 인스턴스가 죽거나 처리가 실패하면 그 작업은 장부에서 사라진다. 그 주문은 DB 복구 스케줄러({@code OrderExpirationTrigger}, 5분)가 처리한다.
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HoldExpirationWorker implements SmartLifecycle {
    static final int BATCH_SIZE = 100;
    private static final long POLL_INTERVAL_MILLIS = 1_000;
    private static final int EXPIRATION_WORKER_COUNT = 2;

    /** KEYS: 1 장부. ARGV: 1 지금(ms), 2 최대 개수. 시각이 지난 작업을 읽고 같은 스크립트에서 지운다. */
    private static final String POP_DUE_SCRIPT = """
            local due = redis.call('zrangebyscore', KEYS[1], '-inf', ARGV[1], 'LIMIT', 0, ARGV[2])
            if #due > 0 then
                redis.call('zrem', KEYS[1], unpack(due))
            end
            return due
            """;

    private final RedissonClient redissonClient;
    private final ExpireOrderUseCase expireOrderUseCase;
    private final Clock clock;

    private @Nullable ScheduledExecutorService poller;
    private @Nullable ThreadPoolTaskExecutor expirationExecutor;

    @Override
    public synchronized void start() {
        final ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 지금 keyspace 만료 리스너의 처리 스레드와 같은 설정이다.
        executor.setCorePoolSize(EXPIRATION_WORKER_COUNT);
        executor.setMaxPoolSize(EXPIRATION_WORKER_COUNT);
        executor.setThreadNamePrefix("hold-expiration-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        expirationExecutor = executor;

        final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            final Thread thread = new Thread(runnable, "hold-expiration-poller");
            thread.setDaemon(true);
            return thread;
        });
        scheduler.scheduleWithFixedDelay(
                this::pollSafely, POLL_INTERVAL_MILLIS, POLL_INTERVAL_MILLIS, TimeUnit.MILLISECONDS);
        poller = scheduler;
    }

    @Override
    public synchronized void stop() {
        if (poller != null) {
            poller.shutdownNow();
            poller = null;
        }
        if (expirationExecutor != null) {
            expirationExecutor.shutdown();
            expirationExecutor = null;
        }
    }

    @Override
    public synchronized boolean isRunning() {
        return poller != null;
    }

    /** 예외가 나가면 예약된 반복이 멈추므로 여기서 받는다. */
    private void pollSafely() {
        try {
            drainDue();
        } catch (final RuntimeException e) {
            log.warn("주문 만료 장부를 확인하지 못했습니다. 다음 주기에 다시 확인합니다.", e);
        }
    }

    void drainDue() {
        final ThreadPoolTaskExecutor executor = expirationExecutor;
        if (executor == null) {
            return;
        }
        while (true) {
            final List<String> due = popDue(clock.instant());
            due.forEach(holdKey -> executor.execute(() -> expire(holdKey)));
            if (due.size() < BATCH_SIZE) {
                return;
            }
        }
    }

    List<String> popDue(final Instant now) {
        return redissonClient
                .getScript(StringCodec.INSTANCE)
                .eval(
                        RScript.Mode.READ_WRITE,
                        POP_DUE_SCRIPT,
                        RScript.ReturnType.LIST,
                        List.of(HoldRedisKey.holdExpiryLedger()),
                        now.toEpochMilli(),
                        BATCH_SIZE);
    }

    private void expire(final String holdKey) {
        try {
            expireOrderUseCase.expireByHoldKey(holdKey, LocalDateTime.now(clock));
            log.info("주문 만료 처리: holdKey={}", holdKey);
        } catch (final RuntimeException e) {
            log.error("주문 만료 처리에 실패했습니다. DB 복구 스케줄러가 다시 처리합니다. holdKey={}", holdKey, e);
        }
    }
}
