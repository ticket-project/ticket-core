package com.ticket.core.infra.lock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ticket.core.domain.hold.model.HoldSnapshot;
import com.ticket.core.domain.hold.store.HoldStore;
import com.ticket.core.domain.performanceseat.command.SeatSelectionService;
import com.ticket.core.domain.performanceseat.command.SeatStatusPublisher;
import com.ticket.core.infra.order.HoldCreationPostCommitProcessor;
import com.ticket.core.infra.metrics.CoreBookingMetrics;
import com.ticket.core.support.lock.DistributedLock;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

@ExtendWith(MockitoExtension.class)
class DistributedLockAopTest {

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RLock lock;

    @Test
    void default_lease_uses_watchdog_try_lock() throws InterruptedException {
        when(redissonClient.getLock("LOCK:test:key")).thenReturn(lock);
        when(lock.tryLock(100L, TimeUnit.MILLISECONDS)).thenReturn(true);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        AspectJProxyFactory proxyFactory = new AspectJProxyFactory(new LockedService());
        proxyFactory.setProxyTargetClass(true);
        proxyFactory.addAspect(new DistributedLockAop(redissonClient, new CoreBookingMetrics(meterRegistry)));
        LockedService proxy = proxyFactory.getProxy();

        proxy.execute("key");

        verify(lock).tryLock(100L, TimeUnit.MILLISECONDS);
        verify(lock).unlock();
    }

    @Test
    void lock_contention_records_only_static_operation_and_reason() throws InterruptedException {
        when(redissonClient.getLock("LOCK:test:key")).thenReturn(lock);
        when(lock.tryLock(100L, TimeUnit.MILLISECONDS)).thenReturn(false);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        AspectJProxyFactory proxyFactory = new AspectJProxyFactory(new LockedService());
        proxyFactory.setProxyTargetClass(true);
        proxyFactory.addAspect(new DistributedLockAop(redissonClient, new CoreBookingMetrics(meterRegistry)));
        LockedService proxy = proxyFactory.getProxy();

        assertThatThrownBy(() -> proxy.execute("key"))
                .isInstanceOf(com.ticket.core.support.exception.CoreException.class);

        assertThat(meterRegistry.get("booking.distributed.lock.acquire.failure")
                .tag("operation", "test")
                .tag("reason", "lock_not_acquired")
                .counter()
                .count()).isEqualTo(1.0);
        assertThat(meterRegistry.getMeters()).allSatisfy(meter -> assertThat(meter.getId().getTags())
                .noneMatch(tag -> tag.getValue().equals("key")));
    }

    @Test
    void hold_creation_post_commit_uses_the_same_performance_seat_lock_key() throws InterruptedException {
        final HoldStore holdStore = mock(HoldStore.class);
        final SeatSelectionService seatSelectionService = mock(SeatSelectionService.class);
        final SeatStatusPublisher seatStatusPublisher = mock(SeatStatusPublisher.class);
        final HoldSnapshot snapshot = new HoldSnapshot(
                "hold-key",
                20L,
                10L,
                List.of(100L),
                LocalDateTime.of(2026, 3, 15, 12, 0)
        );
        when(redissonClient.getLock("LOCK:hold:10:100")).thenReturn(lock);
        when(lock.tryLock(5_000L, TimeUnit.MILLISECONDS)).thenReturn(true);
        when(holdStore.isHeldBy(10L, 100L, "hold-key")).thenReturn(true);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

        final AspectJProxyFactory proxyFactory = new AspectJProxyFactory(
                new HoldCreationPostCommitProcessor(holdStore, seatSelectionService, seatStatusPublisher)
        );
        proxyFactory.setProxyTargetClass(true);
        proxyFactory.addAspect(new DistributedLockAop(redissonClient, new CoreBookingMetrics(meterRegistry)));
        final HoldCreationPostCommitProcessor proxy = proxyFactory.getProxy();

        proxy.process(snapshot);

        verify(redissonClient).getLock("LOCK:hold:10:100");
        verify(lock).tryLock(5_000L, TimeUnit.MILLISECONDS);
        verify(lock).unlock();
    }

    static class LockedService {

        @DistributedLock(prefix = "test", dynamicKey = "#key", waitTime = 100L)
        public void execute(final String key) {
        }
    }
}
